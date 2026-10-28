package hu.robottx.ats.scheduler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_APPLICATION_BY_ID_ENDPOINT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.WiremockTest;
import hu.robottx.ats.annotation.DirtyTest;
import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.repository.ApplicationRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

class ApplicationAiReviewSchedulerTest extends WiremockTest {

    private static final String GEMINI_GENERATE_CONTENT_URL = "/v1beta/models/gemini-2.5-flash:generateContent";
    private static final byte[] SAMPLE_PDF_BYTES = "%PDF-1.4 sample candidate CV".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private ApplicationAiReviewScheduler applicationAIReviewScheduler;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private CircuitBreaker geminiCircuitBreaker;

    @Autowired
    private RateLimiter geminiRateLimiter;

    @Autowired
    private StorageBasedLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
        cleanStorageDirectory();
        geminiCircuitBreaker.reset();
        geminiRateLimiter.changeLimitForPeriod(50);
        lockProvider.clearCache();
        reset(mailSender);
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/job/sql/setup_ai_review_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testReviewApplicationsEvaluatesOnlyEligibleApplications() throws IOException {
        storeCvFile("applications/other-user/cv1.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/third-user/cv2.pdf", SAMPLE_PDF_BYTES);
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .inScenario("BatchReview")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(buildGeminiResponse(88, "Strong Java and Spring Boot candidate.")))
                .willSetStateTo("SecondApplication"));
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .inScenario("BatchReview")
                .whenScenarioStateIs("SecondApplication")
                .willReturn(okJson(buildGeminiResponse(74, "Good cloud architecture background."))));
        applicationAIReviewScheduler.reviewApplications();
        String expectedBase64Pdf = Base64.getEncoder().encodeToString(SAMPLE_PDF_BYTES);
        mockServer.verify(2, postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL)));
        mockServer.verify(postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .withRequestBody(containing("Senior Java Developer"))
                .withRequestBody(containing("I have 8 years of Java and Spring Boot experience."))
                .withRequestBody(containing(expectedBase64Pdf))
                .withRequestBody(containing("\"responseMimeType\":\"application/json\"")));
        mockServer.verify(postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .withRequestBody(containing("Cloud Architect"))
                .withRequestBody(containing("No message provided.")));
        Application app1 = applicationRepository.findById(1L).orElseThrow();
        assertThat(app1.getStatus()).isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(app1.getAiScore()).isEqualTo(88);
        assertThat(app1.getAiReview()).isEqualTo("Strong Java and Spring Boot candidate.");
        Application app2 = applicationRepository.findById(2L).orElseThrow();
        assertThat(app2.getStatus()).isEqualTo(ApplicationStatus.DECLINED);
        assertThat(app2.getAiScore()).isEqualTo(74);
        assertThat(app2.getAiReview()).isEqualTo("Good cloud architecture background.");
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getTo()).containsExactly("third.user@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("InnovateLabs Application Status");
        assertThat(sentMessage.getText()).isEqualTo("Thank you for applying to InnovateLabs.");
        assertThat(applicationRepository.findById(3L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        assertThat(applicationRepository.findById(4L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        assertThat(applicationRepository.findById(5L).orElseThrow().getAiScore())
                .isEqualTo(91);
        assertThat(applicationRepository.findById(6L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        restClient.get()
                .uri(UriComponentsBuilder.fromUriString(JOB_LISTING_APPLICATION_BY_ID_ENDPOINT)
                        .buildAndExpand(1L, 1L)
                        .toUriString())
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(response -> assertJson("job/response/get_reviewed_application_1.json", response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/job/sql/setup_batch_limit_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testReviewApplicationsRespectsBatchSizeAndOldestFirstOrdering() throws IOException {
        storeCvFile("applications/other-user/cv1.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/third-user/cv2.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/other-user/cv3.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/third-user/cv4.pdf", SAMPLE_PDF_BYTES);
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .willReturn(okJson(buildGeminiResponse(80, "Reviewed in batch."))));
        applicationAIReviewScheduler.reviewApplications();
        mockServer.verify(3, postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL)));
        assertThat(applicationRepository.findById(2L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(applicationRepository.findById(3L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(applicationRepository.findById(4L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(applicationRepository.findById(1L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        applicationAIReviewScheduler.reviewApplications();
        mockServer.verify(4, postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL)));
        assertThat(applicationRepository.findById(1L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
    }

    @Test
    @DirtyTest
    @Sql(scripts = {
            "classpath:hu/robottx/ats/job/sql/setup_ai_review_applications.sql",
            "classpath:hu/robottx/ats/job/sql/setup_active_shedlock.sql"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testReviewApplicationsSkippedWhenShedLockIsAlreadyHeld() throws IOException {
        storeCvFile("applications/other-user/cv1.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/third-user/cv2.pdf", SAMPLE_PDF_BYTES);
        applicationAIReviewScheduler.reviewApplications();
        mockServer.verify(0, postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL)));
        assertThat(applicationRepository.findById(1L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        assertThat(applicationRepository.findById(2L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/job/sql/setup_ai_review_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testReviewApplicationsRetriesOnTransientGeminiFailure() throws IOException {
        storeCvFile("applications/other-user/cv1.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/third-user/cv2.pdf", SAMPLE_PDF_BYTES);
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .inScenario("RetryScenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":{\"message\":\"Service Unavailable\"}}"))
                .willSetStateTo("RetrySucceeded"));
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .inScenario("RetryScenario")
                .whenScenarioStateIs("RetrySucceeded")
                .willReturn(okJson(buildGeminiResponse(85, "Recovered after transient failure."))));
        applicationAIReviewScheduler.reviewApplications();
        mockServer.verify(3, postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL)));
        assertThat(applicationRepository.findById(1L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(applicationRepository.findById(2L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/job/sql/setup_batch_limit_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testReviewApplicationsCircuitBreakerAbortsRemainingBatchAndPreservesCommittedReviews() throws IOException {
        storeCvFile("applications/third-user/cv2.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/other-user/cv3.pdf", SAMPLE_PDF_BYTES);
        storeCvFile("applications/third-user/cv4.pdf", SAMPLE_PDF_BYTES);
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .inScenario("CircuitBreakerScenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(buildGeminiResponse(90, "First candidate succeeded.")))
                .willSetStateTo("QuotaExhausted"));
        mockServer.stubFor(post(urlEqualTo(GEMINI_GENERATE_CONTENT_URL))
                .inScenario("CircuitBreakerScenario")
                .whenScenarioStateIs("QuotaExhausted")
                .willReturn(aResponse().withStatus(429).withBody("{\"error\":{\"message\":\"Resource exhausted\"}}")));
        applicationAIReviewScheduler.reviewApplications();
        mockServer.verify(3, postRequestedFor(urlEqualTo(GEMINI_GENERATE_CONTENT_URL)));
        assertThat(geminiCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(applicationRepository.findById(2L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.AI_REVIEWED);
        assertThat(applicationRepository.findById(3L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        assertThat(applicationRepository.findById(4L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
    }

    private void storeCvFile(String objectKey, byte[] content) throws IOException {
        Path filePath = Paths.get("target/test-storage").resolve(objectKey);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content);
    }

    private void cleanStorageDirectory() {
        try {
            Path storagePath = Paths.get("target/test-storage");
            if (Files.exists(storagePath)) {
                try (var stream = Files.walk(storagePath)) {
                    stream.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(storagePath))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String buildGeminiResponse(int score, String review) {
        return """
                {
                  "modelVersion": "gemini-2.5-flash",
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [
                          {
                            "text": "{\\"aiScore\\":%d,\\"aiReview\\":\\"%s\\"}"
                          }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """.formatted(score, review);
    }

}
