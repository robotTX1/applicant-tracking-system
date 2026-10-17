package hu.robottx.ats;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Tag;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.DefaultComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.config.TestConfig;
import hu.robottx.ats.model.CompanyRole;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Tag("IntegrationTest")
@AutoConfigureRestTestClient
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class IntegrationTest {

    protected static final String VALID_USER_JWT_PATH = "token/valid-user.jwt";
    protected static final String INVALID_USER_JWT_PATH = "token/invalid-user.jwt";

    private static final String JSON_ASSERT_MESSAGE = "Expected response: |%s|\nActual response: |%s|";

    @Autowired
    protected RestTestClient restClient;

    private final JsonMapper prettyPrinter = new JsonMapper();

    protected static String loadText(String filePath) {
        try {
            InputStream stream = Objects.requireNonNull(IntegrationTest.class.getResourceAsStream(filePath));
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Failed to load text from {}", filePath, ex);
            throw new RuntimeException("Failed to load text from " + filePath, ex);
        }
    }

    protected static String uriById(String uri, Long id) {
        return UriComponentsBuilder.fromUriString(uri)
                .buildAndExpand(id)
                .toUriString();
    }

    protected static URI uriWithSearchAndPageRequest(String uri, Integer pageSize, Integer pageNumber, String sort,
            String query, Boolean isEmployee, CompanyRole role) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .queryParamIfPresent("pageNumber", Optional.ofNullable(pageNumber))
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .queryParamIfPresent("query", Optional.ofNullable(query))
                .queryParamIfPresent("isEmployee", Optional.ofNullable(isEmployee))
                .queryParamIfPresent("role", Optional.ofNullable(role))
                .build().toUri();
    }

    protected HttpHeaders createAuthorizedHeaders(String tokenPath) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(loadText(tokenPath).trim());
        return headers;
    }

    protected void assertJson(String expectedJsonPath, String actualJson) {
        assertJson(expectedJsonPath, actualJson, new DefaultComparator(JSONCompareMode.STRICT));
    }

    protected void assertJson(String expectedJsonPath, String actualJson, JSONComparator comparator) {
        if (expectedJsonPath != null) {
            String expectedJson = loadText(expectedJsonPath);
            try {
                String message =
                        JSON_ASSERT_MESSAGE.formatted(prettyPrintJson(expectedJson), prettyPrintJson(actualJson));
                JSONAssert.assertEquals(message, expectedJson, actualJson, comparator);
            } catch (JSONException exception) {
                fail("Invalid response", exception);
            }
        }
    }

    private String prettyPrintJson(String json) {
        try {
            Object jsonObject = prettyPrinter.readValue(json, Object.class);
            return prettyPrinter.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception ex) {
            return json;
        }
    }

}
