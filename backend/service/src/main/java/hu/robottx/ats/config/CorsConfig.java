package hu.robottx.ats.config;

import java.util.List;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "cors-config")
@PropertySource(value = "file:${config-directory}/cors.properties", ignoreResourceNotFound = true)
public class CorsConfig {

    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:4200",
            "https://ats.robottx.hu"
    );

    private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
    );

    private List<String> allowedHeaders = List.of("*");

    private List<String> exposedHeaders = List.of(
            "Location",
            "Content-Disposition",
            "Content-Length"
    );

    private boolean allowCredentials = true;

    private long maxAge = 3600L;

}
