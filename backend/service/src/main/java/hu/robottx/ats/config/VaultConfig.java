package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "vault-config")
@ConditionalOnProperty(name = "secret-provider", havingValue = "vault")
@PropertySource(value = "file:${config-directory}/vault.properties", ignoreResourceNotFound = true)
public class VaultConfig {

    private String vaultOcid;
    private String vaultRegion;

    private String dbUsernameSecretName;
    private String dbPasswordSecretName;

}
