package hu.robottx.ats.provider;

public interface SecretProvider {

    String DB_USERNAME_SECRET_NAME = "DB_USERNAME";
    String DB_PASSWORD_SECRET_NAME = "DB_PASSWORD";
    String GEMINI_API_KEY_SECRET_NAME = "GEMINI_API_KEY";
    String MAIL_USERNAME_SECRET_NAME = "MAIL_USERNAME";
    String MAIL_PASSWORD_SECRET_NAME = "MAIL_PASSWORD";

    String getSecret(String name);

    default String getDbUsername() {
        return getSecret(DB_USERNAME_SECRET_NAME);
    }

    default String getDbPassword() {
        return getSecret(DB_PASSWORD_SECRET_NAME);
    }

    default String getGeminiApiKey() {
        return getSecret(GEMINI_API_KEY_SECRET_NAME);
    }

    default String getMailUsername() {
        return getSecret(MAIL_USERNAME_SECRET_NAME);
    }

    default String getMailPassword() {
        return getSecret(MAIL_PASSWORD_SECRET_NAME);
    }

}
