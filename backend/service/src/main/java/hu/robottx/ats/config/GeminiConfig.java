package hu.robottx.ats.config;

import java.time.Duration;

import lombok.Data;

import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "gemini-config")
@PropertySource(value = "file:${config-directory}/gemini.properties", ignoreResourceNotFound = true)
public class GeminiConfig {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are an expert technical recruiter and HR specialist evaluating job applications. "
                    + "Evaluate the candidate's attached CV and applicant message against the job listing title "
                    + "and description. Provide a fair, objective score between 0 and 100 and a concise review "
                    + "summary (maximum 2000 characters).";

    private static final String DEFAULT_USER_PROMPT =
            "Job Title: {jobTitle}\n"
                    + "Job Description:\n{jobDescription}\n\n"
                    + "Applicant Message:\n{applicantMessage}\n\n"
                    + "Please evaluate the attached CV and applicant message for this job listing.";

    // Model & Generation Options
    private String model = "gemini-3.5-flash-lite";
    private GoogleGenAiThinkingLevel thinkingLevel;
    private Integer thinkingBudget;
    private Double temperature = 0.2;
    private Integer maxOutputTokens = 2048;

    // Prompts
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    private String userPrompt = DEFAULT_USER_PROMPT;

    // Scheduled Job & ShedLock
    private String reviewJobCron = "0 */5 * * * *";
    private String reviewJobLockAtMostFor = "PT10M";
    private String reviewJobLockAtLeastFor = "PT10S";
    private int batchSize = 10;

    // Resilience4j RateLimiter
    private int rateLimiterLimitForPeriod = 5;
    private Duration rateLimiterLimitRefreshPeriod = Duration.ofMinutes(1);
    private Duration rateLimiterTimeoutDuration = Duration.ofSeconds(10);

    // Resilience4j Retry
    private int retryMaxAttempts = 3;
    private Duration retryWaitDuration = Duration.ofSeconds(2);

    // Resilience4j CircuitBreaker
    private float circuitBreakerFailureRateThreshold = 50.0f;
    private int circuitBreakerSlidingWindowSize = 10;
    private int circuitBreakerMinimumNumberOfCalls = 5;
    private Duration circuitBreakerWaitDurationInOpenState = Duration.ofMinutes(1);
    private int circuitBreakerPermittedNumberOfCallsInHalfOpenState = 2;

}
