package hu.robottx.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;

import hu.robottx.ats.UnitTest;
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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

class ApplicationAiReviewServiceImplTest extends UnitTest {

    private static final byte[] SAMPLE_PDF_BYTES = "%PDF-1.4 sample cv".getBytes(StandardCharsets.UTF_8);

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private DocumentService documentService;

    @Mock
    private EmailService emailService;

    @Mock
    private ChatModel chatModel;

    private GeminiConfig geminiConfig;
    private RateLimiter rateLimiter;
    private Retry retry;
    private CircuitBreaker circuitBreaker;
    private ApplicationAiReviewServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        geminiConfig = new GeminiConfig();
        geminiConfig.setModel("gemini-3.5-flash");
        geminiConfig.setThinkingLevel(GoogleGenAiThinkingLevel.LOW);
        geminiConfig.setThinkingBudget(1024);
        geminiConfig.setTemperature(0.3);
        geminiConfig.setMaxOutputTokens(1500);
        rateLimiter = RateLimiter.of("testRateLimiter", RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        retry = Retry.of("testRetry", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .ignoreExceptions(
                        CallNotPermittedException.class,
                        RequestNotPermitted.class,
                        StorageException.class,
                        IllegalArgumentException.class)
                .build());
        circuitBreaker = CircuitBreaker.of("testCircuitBreaker", CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .ignoreExceptions(RequestNotPermitted.class, StorageException.class)
                .build());
        lenient().when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        ChatClient chatClient = ChatClient.create(chatModel);
        service = new ApplicationAiReviewServiceImpl(
                applicationRepository,
                documentService,
                emailService,
                geminiConfig,
                chatClient,
                rateLimiter,
                retry,
                circuitBreaker);
    }

    @Test
    @DisplayName("findApplicationsForAiReview returns empty list when batchSize is non-positive")
    void findApplicationsForAiReviewEmptyWhenBatchSizeZero() {
        List<Application> result = service.findApplicationsForAiReview(0);
        assertThat(result).isEmpty();
        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("findApplicationsForAiReview queries APPLIED applications for PUBLISHED AI-enabled jobs")
    void findApplicationsForAiReviewQueriesRepository() {
        Application app = createSampleApplication(1L, "I love Java.");
        when(applicationRepository.findPendingForAiReview(
                ApplicationStatus.APPLIED, JobStatus.PUBLISHED, PageRequest.of(0, 5)))
                .thenReturn(List.of(app));
        List<Application> result = service.findApplicationsForAiReview(5);
        assertThat(result).containsExactly(app);
    }

    @Test
    @DisplayName("evaluateApplication sends prompt, options, and PDF media to ChatModel and returns parsed result")
    void evaluateApplicationSuccess() {
        Application application = createSampleApplication(10L, "Experienced backend developer.");
        mockCvDownload(application.getCvDocument());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(createChatResponse("{\"aiScore\": 88, \"aiReview\": \"Strong Spring Boot candidate.\"}"));
        AiReviewResult result = service.evaluateApplication(application);
        assertThat(result.aiScore()).isEqualTo(88);
        assertThat(result.aiReview()).isEqualTo("Strong Spring Boot candidate.");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt.getSystemMessage().getText()).isEqualTo(geminiConfig.getSystemPrompt());
        assertThat(capturedPrompt.getUserMessage().getText())
                .contains("Senior Java Developer")
                .contains("Build scalable microservices.")
                .contains("Experienced backend developer.");
        assertThat(capturedPrompt.getUserMessage().getMedia()).hasSize(1);
        assertThat(capturedPrompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) capturedPrompt.getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-3.5-flash");
        assertThat(options.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(options.getThinkingBudget()).isEqualTo(1024);
        assertThat(options.getTemperature()).isEqualTo(0.3);
        assertThat(options.getMaxOutputTokens()).isEqualTo(1500);
    }

    @Test
    @DisplayName("evaluateApplication uses default placeholder when applicantMessage is null and clamps/truncates " +
            "output")
    void evaluateApplicationClampsScoreAndTruncatesReview() {
        Application application = createSampleApplication(11L, null);
        mockCvDownload(application.getCvDocument());
        String longReview = "A".repeat(2500);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(createChatResponse("{\"aiScore\": 150, \"aiReview\": \"" + longReview + "\"}"));
        AiReviewResult result = service.evaluateApplication(application);
        assertThat(result.aiScore()).isEqualTo(100);
        assertThat(result.aiReview()).hasSize(2000);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getUserMessage().getText()).contains("No message provided.");
    }

    @Test
    @DisplayName("evaluateApplication retries transient failure and downloads CV only once")
    void evaluateApplicationRetriesAndCachesCvDownload() {
        Application application = createSampleApplication(12L, "Applying now.");
        mockCvDownload(application.getCvDocument());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Transient 503 error"))
                .thenReturn(createChatResponse("{\"aiScore\": -10, \"aiReview\": \"  Needs more experience.  \"}"));
        AiReviewResult result = service.evaluateApplication(application);
        assertThat(result.aiScore()).isZero();
        assertThat(result.aiReview()).isEqualTo("Needs more experience.");
        verify(documentService, times(1)).downloadDocument(application.getCvDocument());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("evaluateApplication throws RequestNotPermitted without downloading CV when rate limiter is exhausted")
    void evaluateApplicationRateLimited() {
        RateLimiter strictRateLimiter = RateLimiter.of("strictLimiter", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        strictRateLimiter.acquirePermission();
        ApplicationAiReviewServiceImpl rateLimitedService = new ApplicationAiReviewServiceImpl(
                applicationRepository,
                documentService,
                emailService,
                geminiConfig,
                ChatClient.create(chatModel),
                strictRateLimiter,
                retry,
                circuitBreaker);
        Application application = createSampleApplication(13L, "Message");
        assertThatThrownBy(() -> rateLimitedService.evaluateApplication(application))
                .isInstanceOf(RequestNotPermitted.class);
        verifyNoInteractions(documentService, chatModel);
    }

    @Test
    @DisplayName("evaluateApplication opens circuit breaker after repeated failures and rejects subsequent calls")
    void evaluateApplicationOpensCircuitBreaker() {
        Application application = createSampleApplication(14L, "Message");
        mockCvDownload(application.getCvDocument());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Quota exhausted"));
        assertThatThrownBy(() -> service.evaluateApplication(application))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Quota exhausted");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(chatModel, times(3)).call(any(Prompt.class));
        assertThatThrownBy(() -> service.evaluateApplication(application))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("evaluateApplication throws StorageException when reading CV stream fails")
    void evaluateApplicationStorageException() throws IOException {
        Application application = createSampleApplication(15L, "Message");
        InputStream brokenStream = mock(InputStream.class);
        when(brokenStream.read(any(byte[].class), any(int.class), any(int.class)))
                .thenThrow(new IOException("Disk read error"));
        when(documentService.downloadDocument(application.getCvDocument()))
                .thenReturn(new DocumentDownload("cv.pdf", "application/pdf", new InputStreamResource(brokenStream)));
        assertThatThrownBy(() -> service.evaluateApplication(application))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to read CV document");
    }

    @Test
    @DisplayName("saveAiReviewResult updates application to AI_REVIEWED when score meets minScore")
    void saveAiReviewResultSuccess() {
        Application application = createSampleApplication(20L, "Hello");
        when(applicationRepository.findById(20L)).thenReturn(Optional.of(application));
        service.saveAiReviewResult(20L, new AiReviewResult(92, "Great match."));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(application.getAiScore()).isEqualTo(92);
        assertThat(application.getAiReview()).isEqualTo("Great match.");
        verify(applicationRepository).save(application);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("saveAiReviewResult moves application to DECLINED and sends email when score is below minScore")
    void saveAiReviewResultDeclinesAndSendsEmailWhenBelowMinScore() {
        Application application = createSampleApplication(20L, "Hello");
        when(applicationRepository.findById(20L)).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(application)).thenReturn(application);
        service.saveAiReviewResult(20L, new AiReviewResult(65, "Not enough experience."));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DECLINED);
        assertThat(application.getAiScore()).isEqualTo(65);
        assertThat(application.getAiReview()).isEqualTo("Not enough experience.");
        verify(applicationRepository).saveAndFlush(application);
        verify(emailService).sendApplicationDecisionEmail(application);
    }

    @Test
    @DisplayName("saveAiReviewResult catches email failure gracefully when moving to DECLINED")
    void saveAiReviewResultCatchesEmailFailureGracefully() {
        Application application = createSampleApplication(20L, "Hello");
        when(applicationRepository.findById(20L)).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(application)).thenReturn(application);
        doThrow(new RuntimeException("Mail server down"))
                .when(emailService).sendApplicationDecisionEmail(application);
        service.saveAiReviewResult(20L, new AiReviewResult(50, "Below minimum."));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DECLINED);
        assertThat(application.getAiScore()).isEqualTo(50);
        verify(applicationRepository).saveAndFlush(application);
        verify(emailService).sendApplicationDecisionEmail(application);
    }

    @Test
    @DisplayName("saveAiReviewResult skips save when application is missing or not in APPLIED status")
    void saveAiReviewResultSkipsWhenNotApplied() {
        when(applicationRepository.findById(21L)).thenReturn(Optional.empty());
        service.saveAiReviewResult(21L, new AiReviewResult(80, "Good"));
        Application alreadyReviewed = createSampleApplication(22L, "Hello");
        alreadyReviewed.setStatus(ApplicationStatus.AI_REVIEWED);
        when(applicationRepository.findById(22L)).thenReturn(Optional.of(alreadyReviewed));
        service.saveAiReviewResult(22L, new AiReviewResult(80, "Good"));
        verify(applicationRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    private Application createSampleApplication(Long applicationId, String message) {
        Document cvDocument = Document.builder()
                .documentId(100L)
                .originalDocumentName("candidate-cv.pdf")
                .mimeType("application/pdf")
                .objectKey("applications/user-1/candidate-cv.pdf")
                .build();
        JobListing jobListing = JobListing.builder()
                .jobListingId(200L)
                .title("Senior Java Developer")
                .description("Build scalable microservices.")
                .aiReviewEnabled(true)
                .aiReviewMinScore(70)
                .status(JobStatus.PUBLISHED)
                .build();
        return Application.builder()
                .applicationId(applicationId)
                .jobListing(jobListing)
                .cvDocument(cvDocument)
                .applicantMessage(message)
                .status(ApplicationStatus.APPLIED)
                .build();
    }

    private void mockCvDownload(Document cvDocument) {
        when(documentService.downloadDocument(cvDocument)).thenAnswer(invocation -> new DocumentDownload(
                cvDocument.getOriginalDocumentName(),
                cvDocument.getMimeType(),
                new ByteArrayResource(SAMPLE_PDF_BYTES)));
    }

    private ChatResponse createChatResponse(String jsonContent) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(jsonContent))));
    }

}
