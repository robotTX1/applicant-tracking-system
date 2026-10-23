package hu.robottx.ats.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hu.robottx.ats.UnitTest;

class CorsConfigTest extends UnitTest {

    @Test
    @DisplayName("CorsConfig has sensible default values")
    void defaultValues() {
        CorsConfig config = new CorsConfig();
        assertThat(config.getAllowedOriginPatterns()).contains(
                "http://localhost:4200",
                "https://ats.robottx.hu"
        );
        assertThat(config.getAllowedMethods()).contains(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        );
        assertThat(config.getAllowedHeaders()).contains("*");
        assertThat(config.getExposedHeaders()).contains("Location", "Content-Disposition", "Content-Length");
        assertThat(config.isAllowCredentials()).isTrue();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("CorsConfig setters and getters work properly")
    void settersAndGetters() {
        CorsConfig config = new CorsConfig();
        config.setAllowedOriginPatterns(List.of("https://example.com"));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("Authorization"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(1800L);
        assertThat(config.getAllowedOriginPatterns()).containsExactly("https://example.com");
        assertThat(config.getAllowedMethods()).containsExactly("GET", "POST");
        assertThat(config.getAllowedHeaders()).containsExactly("Authorization");
        assertThat(config.getExposedHeaders()).containsExactly("Location");
        assertThat(config.isAllowCredentials()).isFalse();
        assertThat(config.getMaxAge()).isEqualTo(1800L);
    }

}
