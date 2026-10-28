package hu.robottx.ats.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import hu.robottx.ats.config.GeminiConfig;
import hu.robottx.ats.domain.AiReviewResult;
import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.entity.Document;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.exception.StorageException;
import hu.robottx.ats.repository.ApplicationRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationAiReviewServiceImpl implements ApplicationAiReviewService {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MAX_REVIEW_LENGTH = 2000;
    private static final String EMPTY_APPLICANT_MESSAGE = "No message provided.";

    private final ApplicationRepository applicationRepository;
    private final DocumentService documentService;
    private final EmailService emailService;
    private final GeminiConfig geminiConfig;
    private final ChatClient chatClient;
    private final RateLimiter geminiRateLimiter;
    private final Retry geminiRetry;
    private final CircuitBreaker geminiCircuitBreaker;

    @Override
    @Transactional(readOnly = true)
    public List<Application> findApplicationsForAiReview(int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }
        return applicationRepository.findPendingForAiReview(
                ApplicationStatus.APPLIED,
                JobStatus.PUBLISHED,
                PageRequest.of(0, batchSize));
    }

    @Override
    public AiReviewResult evaluateApplication(Application application) {
        Document cvDocument = application.getCvDocument();
        MimeType mimeType = MimeTypeUtils.parseMimeType(cvDocument.getMimeType());
        GoogleGenAiChatOptions.Builder optionsBuilder = buildChatOptions();
        AtomicReference<Resource> cvResourceRef = new AtomicReference<>();
        Supplier<AiReviewResult> geminiCall = () -> {
            Resource cvResource = cvResourceRef.updateAndGet(
                    existing -> existing != null ? existing : new ByteArrayResource(readCvBytes(cvDocument)));
            return invokeGemini(application, mimeType, cvResource, optionsBuilder);
        };
        Supplier<AiReviewResult> rateLimitedCall =
                RateLimiter.decorateSupplier(geminiRateLimiter, geminiCall);
        Supplier<AiReviewResult> circuitProtectedCall =
                CircuitBreaker.decorateSupplier(geminiCircuitBreaker, rateLimitedCall);
        Supplier<AiReviewResult> retriedCall =
                Retry.decorateSupplier(geminiRetry, circuitProtectedCall);
        AiReviewResult rawResult = retriedCall.get();
        return sanitizeResult(rawResult);
    }

    @Override
    @Transactional
    public void saveAiReviewResult(Long applicationId, AiReviewResult result) {
        Application application = findAppliedApplication(applicationId);
        if (application == null) {
            return;
        }

        application.setAiScore(result.aiScore());
        application.setAiReview(result.aiReview());

        if (result.aiScore() < application.getJobListing().getAiReviewMinScore()) {
            declineApplication(application, result.aiScore());
        } else {
            markReviewed(application, result.aiScore());
        }
    }

    private Application findAppliedApplication(Long applicationId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            log.debug("Application {} no longer exists, skipping AI review save", applicationId);
            return null;
        }
        if (application.getStatus() != ApplicationStatus.APPLIED) {
            log.debug("Application {} is in status {}, skipping AI review save",
                    applicationId, application.getStatus());
            return null;
        }
        return application;
    }

    private void declineApplication(Application application, int score) {
        final int minScore = application.getJobListing().getAiReviewMinScore();
        application.setStatus(ApplicationStatus.DECLINED);
        applicationRepository.saveAndFlush(application);
        sendDeclineEmailSafely(application);
        log.debug("Application {} AI score {} is below min score {}, moved to DECLINED",
                application.getApplicationId(), score, minScore);
    }

    private void markReviewed(Application application, int score) {
        application.setStatus(ApplicationStatus.AI_REVIEWED);
        applicationRepository.save(application);
        log.debug("Saved AI review for application {} with score {}",
                application.getApplicationId(), score);
    }

    private void sendDeclineEmailSafely(Application application) {
        try {
            emailService.sendApplicationDecisionEmail(application);
        } catch (Exception ex) {
            log.warn("Failed to send decline email for application {}: {}",
                    application.getApplicationId(), ex.getMessage(), ex);
        }
    }

    private byte[] readCvBytes(Document cvDocument) {
        DocumentDownload download = documentService.downloadDocument(cvDocument);
        try (InputStream inputStream = download.resource().getInputStream()) {
            return IOUtils.toByteArray(inputStream);
        } catch (IOException ex) {
            throw new StorageException(
                    "Failed to read CV document with id: " + cvDocument.getDocumentId(), ex);
        }
    }

    private GoogleGenAiChatOptions.Builder buildChatOptions() {
        GoogleGenAiChatOptions.Builder builder = GoogleGenAiChatOptions.builder()
                .model(geminiConfig.getModel());
        if (geminiConfig.getTemperature() != null) {
            builder.temperature(geminiConfig.getTemperature());
        }
        if (geminiConfig.getMaxOutputTokens() != null) {
            builder.maxOutputTokens(geminiConfig.getMaxOutputTokens());
        }
        if (geminiConfig.getThinkingLevel() != null) {
            builder.thinkingLevel(geminiConfig.getThinkingLevel());
        }
        if (geminiConfig.getThinkingBudget() != null) {
            builder.thinkingBudget(geminiConfig.getThinkingBudget());
        }
        return builder;
    }

    private AiReviewResult invokeGemini(Application application,
            MimeType mimeType,
            Resource cvResource,
            GoogleGenAiChatOptions.Builder optionsBuilder) {
        JobListing jobListing = application.getJobListing();
        String applicantMessage = StringUtils.hasText(application.getApplicantMessage())
                ? application.getApplicantMessage()
                : EMPTY_APPLICANT_MESSAGE;
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .options(optionsBuilder)
                .user(userSpec -> userSpec
                        .text(geminiConfig.getUserPrompt())
                        .param("jobTitle", jobListing.getTitle())
                        .param("jobDescription", jobListing.getDescription())
                        .param("applicantMessage", applicantMessage)
                        .media(mimeType, cvResource));
        if (StringUtils.hasText(geminiConfig.getSystemPrompt())) {
            requestSpec.system(geminiConfig.getSystemPrompt());
        }
        AiReviewResult result = requestSpec.call()
                .entity(AiReviewResult.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
        if (result == null || result.aiScore() == null || !StringUtils.hasText(result.aiReview())) {
            throw new IllegalStateException(
                    "Gemini returned an incomplete AI review response for application "
                            + application.getApplicationId());
        }
        return result;
    }

    private AiReviewResult sanitizeResult(AiReviewResult rawResult) {
        int clampedScore = Math.clamp(rawResult.aiScore(), MIN_SCORE, MAX_SCORE);
        String trimmedReview = rawResult.aiReview().trim();
        if (trimmedReview.length() > MAX_REVIEW_LENGTH) {
            trimmedReview = trimmedReview.substring(0, MAX_REVIEW_LENGTH);
        }
        return new AiReviewResult(clampedScore, trimmedReview);
    }

}
