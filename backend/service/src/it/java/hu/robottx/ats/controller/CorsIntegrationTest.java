package hu.robottx.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;

import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_ENDPOINT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import hu.robottx.ats.WiremockTest;

class CorsIntegrationTest extends WiremockTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String DISALLOWED_ORIGIN = "https://unauthorized-domain.com";

    @Test
    @DisplayName("Preflight OPTIONS request from allowed origin succeeds with CORS headers")
    void testPreflightAllowedOrigin() {
        restClient.options()
                .uri(CURRENT_USER_ENDPOINT)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods ->
                        assertThat(methods).contains(HttpMethod.GET.name()))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAge ->
                        assertThat(maxAge).isEqualTo("3600"));
    }

    @Test
    @DisplayName("Preflight OPTIONS request from disallowed origin is rejected with 403 Forbidden")
    void testPreflightDisallowedOrigin() {
        restClient.options()
                .uri(CURRENT_USER_ENDPOINT)
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Simple GET request with Origin header includes CORS headers in response")
    void testActualRequestWithOriginHeader() {
        restClient.get()
                .uri("/api.yaml")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeaders ->
                        assertThat(exposedHeaders).contains("Location", "Content-Disposition"));
    }

}
