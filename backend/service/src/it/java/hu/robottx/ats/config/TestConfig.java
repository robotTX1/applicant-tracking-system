package hu.robottx.ats.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration
public class TestConfig {

    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Europe/Budapest");
    private static final ZonedDateTime FIXED_TIME = ZonedDateTime.of(2025, 8, 16, 15, 30, 10, 0, TEST_ZONE_ID);

    @Bean
    @ServiceConnection
    public MySQLContainer mySQLContainer() {
        return new MySQLContainer("mysql:26.7.0")
                .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw"))
                .withCommand(
                        "--innodb-flush-log-at-trx-commit=0",
                        "--innodb-doublewrite=0",
                        "--sync-binlog=0"
                );
    }

    @Bean
    @Primary
    public Clock clock() {
        return Clock.fixed(FIXED_TIME.toInstant(), TEST_ZONE_ID);
    }

    @Bean
    @Primary
    public DataSource getDataSource(MySQLContainer mysqlContainer) {
        return DataSourceBuilder.create()
                .url(mysqlContainer.getJdbcUrl())
                .username(mysqlContainer.getUsername())
                .password(mysqlContainer.getPassword())
                .build();
    }

    @Bean
    @Primary
    public Client googleGenAiClient() {
        return Client.builder()
                .apiKey("test-api-key")
                .httpOptions(HttpOptions.builder()
                        .baseUrl("http://localhost:1080")
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }

    @Primary
    @Bean(name = "emailTaskExecutor")
    public TaskExecutor emailTaskExecutor() {
        return new SyncTaskExecutor();
    }

}
