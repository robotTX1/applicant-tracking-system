package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "database-config")
@ConditionalOnProperty(name = "secret-provider", havingValue = "local")
@PropertySource(value = "file:${config-directory}/database.properties", ignoreResourceNotFound = true)
public class DatabaseConfig {

    private String username;
    private String password;

}
