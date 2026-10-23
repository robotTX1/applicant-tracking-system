package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "gemini-config")
@ConditionalOnProperty(name = "secret-provider", havingValue = "local")
@PropertySource(value = "file:${config-directory}/gemini.properties", ignoreResourceNotFound = true)
public class GeminiSecretConfig {

    private String apikey;

}
