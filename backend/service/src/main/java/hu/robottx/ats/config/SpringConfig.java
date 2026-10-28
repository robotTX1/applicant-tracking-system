package hu.robottx.ats.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.secrets.SecretsClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import hu.robottx.ats.exception.StorageException;
import hu.robottx.ats.provider.SecretProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import tools.jackson.core.json.JsonReadFeature;

@EnableAsync
@Configuration
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class SpringConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("provisionedUsers");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(10_000));
        return cacheManager;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> builder
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
                .build();
    }


    @Bean
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    @Bean
    public DataSource getDataSource(ServiceConfig serviceConfig, SecretProvider secretProvider) {
        return DataSourceBuilder.create()
                .url(serviceConfig.getDatabaseUrl())
                .username(secretProvider.getDbUsername())
                .password(secretProvider.getDbPassword())
                .build();
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }

    @Bean
    public JavaMailSender javaMailSender(MailConfig mailConfig, SecretProvider secretProvider) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailConfig.getHost());
        mailSender.setPort(mailConfig.getPort());
        mailSender.setProtocol(mailConfig.getProtocol());
        mailSender.setUsername(secretProvider.getMailUsername());
        mailSender.setPassword(secretProvider.getMailPassword());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", mailConfig.getProtocol());
        props.put("mail.smtp.auth", String.valueOf(mailConfig.isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(mailConfig.isStarttlsEnable()));
        return mailSender;
    }

    @Bean(name = "emailTaskExecutor")
    public TaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("email-exec-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Client googleGenAiClient(SecretProvider secretProvider) {
        return Client.builder()
                .apiKey(secretProvider.getGeminiApiKey())
                .httpOptions(HttpOptions.builder()
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }

    @Bean
    public RetryTemplate retryTemplate() {
        return new RetryTemplate(RetryPolicy.builder().maxRetries(0).build());
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean
    public RateLimiter geminiRateLimiter(GeminiConfig geminiConfig) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(geminiConfig.getRateLimiterLimitForPeriod())
                .limitRefreshPeriod(geminiConfig.getRateLimiterLimitRefreshPeriod())
                .timeoutDuration(geminiConfig.getRateLimiterTimeoutDuration())
                .build();
        return RateLimiter.of("geminiRateLimiter", config);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public Retry geminiRetry(GeminiConfig geminiConfig) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(geminiConfig.getRetryMaxAttempts())
                .waitDuration(geminiConfig.getRetryWaitDuration())
                .ignoreExceptions(
                        CallNotPermittedException.class,
                        RequestNotPermitted.class,
                        StorageException.class,
                        IllegalArgumentException.class)
                .build();
        return Retry.of("geminiRetry", config);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public CircuitBreaker geminiCircuitBreaker(GeminiConfig geminiConfig) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(geminiConfig.getCircuitBreakerFailureRateThreshold())
                .slidingWindowSize(geminiConfig.getCircuitBreakerSlidingWindowSize())
                .minimumNumberOfCalls(geminiConfig.getCircuitBreakerMinimumNumberOfCalls())
                .waitDurationInOpenState(geminiConfig.getCircuitBreakerWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(
                        geminiConfig.getCircuitBreakerPermittedNumberOfCallsInHalfOpenState())
                .ignoreExceptions(RequestNotPermitted.class, StorageException.class)
                .enableAutomaticTransitionFromOpenToHalfOpen()
                .build();
        return CircuitBreaker.of("geminiCircuitBreaker", config);
    }

    @Bean
    @ConditionalOnProperty(name = "secret-provider", havingValue = "vault")
    public SecretsClient secretsClient(AbstractAuthenticationDetailsProvider authenticationDetailsProvider) {
        return SecretsClient.builder().build(authenticationDetailsProvider);
    }

    @Bean
    @ConditionalOnProperty(name = "storage-provider", havingValue = "oci")
    public ObjectStorageClient objectStorageClient(AbstractAuthenticationDetailsProvider authenticationDetailsProvider,
            OciStorageConfig ociStorageConfig) {
        return ObjectStorageClient.builder()
                .region(Region.fromRegionId(ociStorageConfig.getRegion()))
                .build(authenticationDetailsProvider);
    }

    @Bean
    @ConditionalOnExpression("'${secret-provider:}'.equals('vault') or '${storage-provider:}'.equals('oci')")
    public AbstractAuthenticationDetailsProvider instancePrincipalDetailsProvider() {
        return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
    }

}
