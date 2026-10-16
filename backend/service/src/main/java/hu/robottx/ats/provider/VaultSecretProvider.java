package hu.robottx.ats.provider;

import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleByNameRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleByNameResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import hu.robottx.ats.config.VaultConfig;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secret-provider", havingValue = "vault")
public class VaultSecretProvider implements SecretProvider {

    private final VaultConfig vaultConfig;
    private final SecretsClient secretsClient;
    private final Map<String, String> secrets = new HashMap<>();

    @PostConstruct
    public void init() {
        log.debug("Fetching secrets from the Vault");
        secrets.put(DB_USERNAME_SECRET_NAME, getSecretFromVault(secretsClient, vaultConfig.getDbUsernameSecretName()));
        secrets.put(DB_PASSWORD_SECRET_NAME, getSecretFromVault(secretsClient, vaultConfig.getDbPasswordSecretName()));
        log.debug("Successfully fetched the secrets from the Vault");
    }

    @Override
    public String getSecret(String name) {
        return secrets.get(name);
    }

    private String getSecretFromVault(SecretsClient client, String secretName) {
        GetSecretBundleByNameRequest secretBundleRequest = GetSecretBundleByNameRequest.builder()
                .vaultId(vaultConfig.getVaultOcid())
                .secretName(secretName)
                .build();
        GetSecretBundleByNameResponse secretBundle = client.getSecretBundleByName(secretBundleRequest);
        Base64SecretBundleContentDetails secretBundleContent =
                (Base64SecretBundleContentDetails) secretBundle.getSecretBundle().getSecretBundleContent();
        return new String(Base64.getDecoder().decode(secretBundleContent.getContent()));
    }

}
