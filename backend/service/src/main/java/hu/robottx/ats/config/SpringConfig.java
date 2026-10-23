package hu.robottx.ats.config;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.secrets.SecretsClient;

import java.time.Clock;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import hu.robottx.ats.provider.SecretProvider;

@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class SpringConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
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
    @ConditionalOnProperty(name = "storage-provider", havingValue = "oci")
    public ObjectStorageClient objectStorageClient(AbstractAuthenticationDetailsProvider authenticationDetailsProvider,
            OciStorageConfig ociStorageConfig) {
        return ObjectStorageClient.builder()
                .region(Region.fromRegionId(ociStorageConfig.getRegion()))
                .build(authenticationDetailsProvider);
    }

    @Bean
    @ConditionalOnExpression("'${secret-provider:}'.equals('vault') or '${storage-provider:}'.equals('oci')")
    public AbstractAuthenticationDetailsProvider instancePrincipalDetailsProvider() {
        return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
    }

}
