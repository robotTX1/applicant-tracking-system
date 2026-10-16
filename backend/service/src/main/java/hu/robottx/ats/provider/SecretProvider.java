package hu.robottx.ats.provider;

public interface SecretProvider {

    String DB_USERNAME_SECRET_NAME = "DB_USERNAME";
    String DB_PASSWORD_SECRET_NAME = "DB_PASSWORD";

    String getSecret(String name);

    default String getDbUsername() {
        return getSecret(DB_USERNAME_SECRET_NAME);
    }

    default String getDbPassword() {
        return getSecret(DB_PASSWORD_SECRET_NAME);
    }

}
