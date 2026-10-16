package hu.robottx.ats.config;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import hu.robottx.ats.provider.SecretProvider;

@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
public class SpringConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DataSource getDataSource(ServiceConfig serviceConfig, SecretProvider secretProvider) {
        return DataSourceBuilder.create()
                .url(serviceConfig.getDatabaseUrl())
                .username(secretProvider.getDbUsername())
                .password(secretProvider.getDbPassword())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "secret-provider", havingValue = "vault")
    public SecretsClient secretsClient(AbstractAuthenticationDetailsProvider authenticationDetailsProvider) {
        return SecretsClient.builder().build(authenticationDetailsProvider);
    }

    @Bean
    @ConditionalOnProperty(name = "secret-provider", havingValue = "vault")
    public AbstractAuthenticationDetailsProvider instancePrincipalDetailsProvider() {
        return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
    }

}
