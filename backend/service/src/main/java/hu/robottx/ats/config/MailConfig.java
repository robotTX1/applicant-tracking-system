package hu.robottx.ats.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@ConfigurationProperties(prefix = "mail-config")
@PropertySource(value = "file:${config-directory}/mail.properties", ignoreResourceNotFound = true)
public class MailConfig {

    private String host;
    private int port;
    private String from;
    private String protocol = "smtp";
    private boolean auth = true;
    private boolean starttlsEnable = true;

}
