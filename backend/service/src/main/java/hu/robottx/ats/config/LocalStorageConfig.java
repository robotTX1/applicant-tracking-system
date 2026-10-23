package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "local-storage-config")
@ConditionalOnProperty(name = "storage-provider", havingValue = "local", matchIfMissing = true)
@PropertySource(value = "file:${config-directory}/storage.properties", ignoreResourceNotFound = true)
public class LocalStorageConfig {

    private String directory = "storage";

}
