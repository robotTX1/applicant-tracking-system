package hu.robottx.ats.scheduler;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import hu.robottx.ats.config.GeminiConfig;
import hu.robottx.ats.domain.AiReviewResult;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.service.ApplicationAiReviewService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationAiReviewScheduler {

    private final ApplicationAiReviewService applicationAiReviewService;
    private final GeminiConfig geminiConfig;
    private final CircuitBreaker geminiCircuitBreaker;

    @Scheduled(cron = "${gemini-config.reviewJobCron:0 */5 * * * *}")
    @SchedulerLock(
            name = "aiApplicationReviewJob",
            lockAtMostFor = "${gemini-config.reviewJobLockAtMostFor:PT10M}",
            lockAtLeastFor = "${gemini-config.reviewJobLockAtLeastFor:PT10S}"
    )
    public void reviewApplications() {
        LockAssert.assertLocked();
        int batchSize = geminiConfig.getBatchSize();
        List<Application> applications = applicationAiReviewService.findApplicationsForAiReview(batchSize);
        if (applications.isEmpty()) {
            log.debug("No pending applications found for AI review");
            return;
        }
        log.info("Starting AI review job for {} pending applications", applications.size());
        int reviewedCount = 0;
        for (Application application : applications) {
            Long applicationId = application.getApplicationId();
            try {
                AiReviewResult result = applicationAiReviewService.evaluateApplication(application);
                applicationAiReviewService.saveAiReviewResult(applicationId, result);
                reviewedCount++;
            } catch (RequestNotPermitted | CallNotPermittedException ex) {
                log.warn("Stopping AI review batch at application {} due to Resilience4j protection: {}",
                        applicationId, ex.getMessage());
                break;
            } catch (Exception ex) {
                log.error("Failed to perform AI review for application {}", applicationId, ex);
                CircuitBreaker.State state = geminiCircuitBreaker.getState();
                if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                    log.warn("Circuit breaker is {}, stopping remaining AI review batch", state);
                    break;
                }
            }
        }
        log.info("Completed AI review job: reviewed {}/{} applications", reviewedCount, applications.size());
    }

}
