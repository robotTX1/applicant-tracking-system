package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "service-config")
@PropertySource(value = "file:${config-directory}/service.properties", ignoreResourceNotFound = true)
public class ServiceConfig {

    // Database
    private String databaseUrl;

    // OAuth
    private String authorizationServerIssuerUri;
    private String authorizationServerJwksUri;

}
