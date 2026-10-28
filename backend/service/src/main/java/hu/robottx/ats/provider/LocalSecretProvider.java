package hu.robottx.ats.provider;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import hu.robottx.ats.config.DatabaseConfig;
import hu.robottx.ats.config.GeminiSecretConfig;
import hu.robottx.ats.config.MailSecretConfig;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secret-provider", havingValue = "local")
public class LocalSecretProvider implements SecretProvider {

    private final DatabaseConfig databaseConfig;
    private final GeminiSecretConfig geminiSecretConfig;
    private final MailSecretConfig mailSecretConfig;
    private final Map<String, String> secrets = new HashMap<>();

    @PostConstruct
    public void init() {
        secrets.put(DB_USERNAME_SECRET_NAME, databaseConfig.getUsername());
        secrets.put(DB_PASSWORD_SECRET_NAME, databaseConfig.getPassword());
        secrets.put(GEMINI_API_KEY_SECRET_NAME, geminiSecretConfig.getApikey());
        secrets.put(MAIL_USERNAME_SECRET_NAME, mailSecretConfig.getUsername());
        secrets.put(MAIL_PASSWORD_SECRET_NAME, mailSecretConfig.getPassword());
    }

    @Override
    public String getSecret(String name) {
        return secrets.get(name);
    }

}
