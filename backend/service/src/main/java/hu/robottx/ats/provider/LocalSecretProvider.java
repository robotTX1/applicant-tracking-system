package hu.robottx.ats.provider;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import hu.robottx.ats.config.DatabaseConfig;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secret-provider", havingValue = "local")
public class LocalSecretProvider implements SecretProvider {

    private final DatabaseConfig databaseConfig;
    private final Map<String, String> secrets = new HashMap<>();

    @PostConstruct
    public void init() {
        secrets.put(DB_USERNAME_SECRET_NAME, databaseConfig.getUsername());
        secrets.put(DB_PASSWORD_SECRET_NAME, databaseConfig.getPassword());
    }

    @Override
    public String getSecret(String name) {
        return secrets.get(name);
    }

}
