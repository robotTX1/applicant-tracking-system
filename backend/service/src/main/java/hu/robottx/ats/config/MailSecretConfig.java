package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "mail-config")
@ConditionalOnProperty(name = "secret-provider", havingValue = "local")
@PropertySource(value = "file:${config-directory}/mail.properties", ignoreResourceNotFound = true)
public class MailSecretConfig {

    private String username;
    private String password;

}
