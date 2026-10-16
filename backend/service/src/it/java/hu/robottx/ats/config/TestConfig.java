package hu.robottx.ats.applicanttrackingsystem.config;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration
@EnableWebSecurity(debug = true)
public class TestConfig {

    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Europe/Budapest");
    private static final ZonedDateTime FIXED_TIME = ZonedDateTime.of(2025, 8, 16, 15, 30, 10, 0, TEST_ZONE_ID);

    @Bean
    public MySQLContainer mySQLContainer() {
        return new MySQLContainer("mysql:26.7.0");
    }

    @Bean
    @Primary
    public Clock clock() {
        return Clock.fixed(FIXED_TIME.toInstant(), TEST_ZONE_ID);
    }

}
