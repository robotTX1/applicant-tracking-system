package hu.robottx.ats;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class WiremockTest extends IntegrationTest {

    private static final String JWKS_URL = "/realms/ats/protocol/openid-connect/certs";
    private static final String JWKS_RESPONSE = "secret/jwks.json";

    protected static WireMockServer mockServer;

    @BeforeAll
    static void beforeAll() {
        startMockServer();
    }

    @AfterAll
    static void afterAll() {
        stopMockServer();
    }

    protected static void startMockServer() {
        mockServer = new WireMockServer(options().port(1080));
        mockServer.start();
    }

    protected static void stopMockServer() {
        mockServer.stop();
    }

    protected static void createDefaultExpectations() {
        createExpectationForJwks();
    }

    private static void createExpectationForJwks() {
        mockServer.stubFor(get(JWKS_URL)
                .willReturn(okJson(loadText(JWKS_RESPONSE))));
    }

}
