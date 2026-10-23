package hu.robottx.ats.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.config.GeminiConfig;
import hu.robottx.ats.domain.AiReviewResult;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.scheduler.ApplicationAiReviewScheduler;
import hu.robottx.ats.service.ApplicationAiReviewService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import net.javacrumbs.shedlock.core.LockAssert;

class ApplicationAiReviewSchedulerUnitTest extends UnitTest {

    @Mock
    private ApplicationAiReviewService applicationAiReviewService;

    @Mock
    private CircuitBreaker geminiCircuitBreaker;

    private GeminiConfig geminiConfig;
    private ApplicationAiReviewScheduler scheduler;

    @BeforeEach
    void setUp() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        geminiConfig = new GeminiConfig();
        geminiConfig.setBatchSize(3);
        scheduler = new ApplicationAiReviewScheduler(applicationAiReviewService, geminiConfig, geminiCircuitBreaker);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    @DisplayName("reviewApplications does nothing when no pending applications exist")
    void reviewApplicationsEmptyBatch() {
        when(applicationAiReviewService.findApplicationsForAiReview(3)).thenReturn(List.of());
        scheduler.reviewApplications();
        verify(applicationAiReviewService).findApplicationsForAiReview(3);
        verify(applicationAiReviewService, never()).evaluateApplication(any());
        verify(applicationAiReviewService, never()).saveAiReviewResult(any(), any());
    }

    @Test
    @DisplayName("reviewApplications evaluates and saves all applications in batch")
    void reviewApplicationsProcessesBatch() {
        Application app1 = Application.builder().applicationId(1L).build();
        Application app2 = Application.builder().applicationId(2L).build();
        AiReviewResult result1 = new AiReviewResult(85, "Good fit");
        AiReviewResult result2 = new AiReviewResult(60, "Moderate fit");
        when(applicationAiReviewService.findApplicationsForAiReview(3)).thenReturn(List.of(app1, app2));
        when(applicationAiReviewService.evaluateApplication(app1)).thenReturn(result1);
        when(applicationAiReviewService.evaluateApplication(app2)).thenReturn(result2);
        scheduler.reviewApplications();
        verify(applicationAiReviewService).saveAiReviewResult(1L, result1);
        verify(applicationAiReviewService).saveAiReviewResult(2L, result2);
    }

    @Test
    @DisplayName("reviewApplications aborts remaining batch when RateLimiter rejects a call")
    void reviewApplicationsStopsOnRateLimit() {
        Application app1 = Application.builder().applicationId(1L).build();
        Application app2 = Application.builder().applicationId(2L).build();
        Application app3 = Application.builder().applicationId(3L).build();
        AiReviewResult result1 = new AiReviewResult(90, "Excellent");
        RateLimiter rateLimiter = RateLimiter.of("testLimiter", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
        RequestNotPermitted notPermitted = RequestNotPermitted.createRequestNotPermitted(rateLimiter);
        when(applicationAiReviewService.findApplicationsForAiReview(3)).thenReturn(List.of(app1, app2, app3));
        when(applicationAiReviewService.evaluateApplication(app1)).thenReturn(result1);
        when(applicationAiReviewService.evaluateApplication(app2)).thenThrow(notPermitted);
        scheduler.reviewApplications();
        verify(applicationAiReviewService).saveAiReviewResult(1L, result1);
        verify(applicationAiReviewService, never()).saveAiReviewResult(eq(2L), any());
        verify(applicationAiReviewService, never()).evaluateApplication(app3);
    }

    @Test
    @DisplayName("reviewApplications continues batch on single application error when circuit breaker stays closed")
    void reviewApplicationsContinuesOnNonCircuitBreakerFailure() {
        Application app1 = Application.builder().applicationId(1L).build();
        Application app2 = Application.builder().applicationId(2L).build();
        AiReviewResult result2 = new AiReviewResult(75, "Solid candidate");
        when(applicationAiReviewService.findApplicationsForAiReview(3)).thenReturn(List.of(app1, app2));
        when(applicationAiReviewService.evaluateApplication(app1))
                .thenThrow(new RuntimeException("Missing CV file in storage"));
        when(geminiCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(applicationAiReviewService.evaluateApplication(app2)).thenReturn(result2);
        scheduler.reviewApplications();
        verify(applicationAiReviewService, never()).saveAiReviewResult(eq(1L), any());
        verify(applicationAiReviewService).saveAiReviewResult(2L, result2);
    }

    @Test
    @DisplayName("reviewApplications stops batch when failure causes circuit breaker to open")
    void reviewApplicationsStopsWhenCircuitBreakerOpens() {
        Application app1 = Application.builder().applicationId(1L).build();
        Application app2 = Application.builder().applicationId(2L).build();
        when(applicationAiReviewService.findApplicationsForAiReview(3)).thenReturn(List.of(app1, app2));
        when(applicationAiReviewService.evaluateApplication(app1))
                .thenThrow(new RuntimeException("Gemini API down"));
        when(geminiCircuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        scheduler.reviewApplications();
        verify(applicationAiReviewService, never()).saveAiReviewResult(eq(1L), any());
        verify(applicationAiReviewService, never()).evaluateApplication(app2);
    }

    @Test
    @DisplayName("reviewApplications stops batch when CallNotPermittedException is thrown")
    void reviewApplicationsStopsOnCallNotPermittedException() {
        Application app1 = Application.builder().applicationId(1L).build();
        Application app2 = Application.builder().applicationId(2L).build();
        CallNotPermittedException callNotPermitted = mock(CallNotPermittedException.class);
        when(applicationAiReviewService.findApplicationsForAiReview(3)).thenReturn(List.of(app1, app2));
        when(applicationAiReviewService.evaluateApplication(app1)).thenThrow(callNotPermitted);
        scheduler.reviewApplications();
        verify(applicationAiReviewService, never()).saveAiReviewResult(any(), any());
        verify(applicationAiReviewService, never()).evaluateApplication(app2);
    }

}
