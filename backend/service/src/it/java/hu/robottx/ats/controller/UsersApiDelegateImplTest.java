package hu.robottx.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;

import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_CV_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_CV_METADATA_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.USERS_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.WiremockTest;
import hu.robottx.ats.annotation.DirtyTest;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.repository.UserRepository;

class UsersApiDelegateImplTest extends WiremockTest {

    private static final String USER_LOOKUP_ENDPOINT = USERS_ENDPOINT + "/lookup";
    private static final String NO_ID_USER_JWT_PATH = "token/no-id-user.jwt";
    private static final String NO_FIRST_NAME_USER_JWT_PATH = "token/no-first-name-user.jwt";
    private static final String NO_LAST_NAME_USER_JWT_PATH = "token/no-last-name-user.jwt";
    private static final String NO_EMAIL_USER_JWT_PATH = "token/no-email-user.jwt";
    private static final String NO_PHONE_NUMBER_USER_JWT_PATH = "token/no-phone-number-user.jwt";
    private static final byte[] SAMPLE_CV_CONTENT = "%PDF-1.4 sample CV content".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
        cleanStorageDirectory();
        Optional.ofNullable(cacheManager.getCache("provisionedUsers")).ifPresent(Cache::clear);
    }

    @DirtyTest
    @MethodSource("getCurrentUserTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetCurrentUser(String description,
            String jwtPath,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.get()
                .uri(CURRENT_USER_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @Test
    @DirtyTest
    void testAutoProvisioningOnAuthenticatedEndpoint() {
        userRepository.deleteById("test-user");
        assertThat(userRepository.findById("test-user")).isEmpty();
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
        Optional<User> provisionedUser = userRepository.findById("test-user");
        assertThat(provisionedUser).isPresent();
        assertThat(provisionedUser.get().getFirstName()).isEqualTo("Test");
        assertThat(provisionedUser.get().getLastName()).isEqualTo("User");
        assertThat(provisionedUser.get().getEmail()).isEqualTo("test.user@example.com");
        assertThat(provisionedUser.get().getPhoneNumber()).isEqualTo("+36301234567");
    }

    @Test
    @DirtyTest
    void testUploadCurrentUserCv() {
        MultiValueMap<String, Object> body = createMultipartBody(
                "resume.pdf", SAMPLE_CV_CONTENT, MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectHeader().valueEquals(HttpHeaders.LOCATION, CURRENT_USER_CV_ENDPOINT)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/post_current_user_cv.json", response));
    }

    @Test
    @DirtyTest
    void testUploadCurrentUserCvReplacesExistingCv() {
        uploadCv("first_resume.pdf", SAMPLE_CV_CONTENT);
        byte[] newContent = "%PDF-1.4 updated CV content".getBytes(StandardCharsets.UTF_8);
        MultiValueMap<String, Object> body = createMultipartBody(
                "second_resume.pdf", newContent, MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/post_current_user_cv_replacement.json", response));
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, disp ->
                        assertThat(disp).contains("filename=\"second_resume.pdf\""))
                .expectBody(byte[].class)
                .isEqualTo(newContent);
    }

    @Test
    @DirtyTest
    void testGetCurrentUserCv() {
        uploadCv("my_cv.pdf", SAMPLE_CV_CONTENT);
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, disp ->
                        assertThat(disp).contains("filename=\"my_cv.pdf\""))
                .expectBody(byte[].class)
                .isEqualTo(SAMPLE_CV_CONTENT);
    }

    @Test
    @DirtyTest
    void testGetCurrentUserCvMetadata() {
        uploadCv("my_cv.pdf", SAMPLE_CV_CONTENT);
        restClient.get()
                .uri(CURRENT_USER_CV_METADATA_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_metadata.json", response));
    }

    @Test
    @DirtyTest
    void testDeleteCurrentUserCv() {
        uploadCv("to_delete.pdf", SAMPLE_CV_CONTENT);
        restClient.delete()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_not_found.json", response));
        restClient.get()
                .uri(CURRENT_USER_CV_METADATA_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_not_found.json", response));
        restClient.delete()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_not_found.json", response));
    }

    @Test
    void testCvNotFoundWhenNoCvUploaded() {
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_not_found.json", response));
        restClient.get()
                .uri(CURRENT_USER_CV_METADATA_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_not_found.json", response));
        restClient.delete()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_cv_not_found.json", response));
    }

    @Test
    void testUploadCvFileTooLarge() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1024];
        MultiValueMap<String, Object> body = createMultipartBody(
                "large.pdf", largeBytes, MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody(String.class)
                .value(response -> assertJson(
                        "user/response/post_current_user_cv_file_too_large.json", response));
    }

    @Test
    void testUploadCvEmptyFile() {
        MultiValueMap<String, Object> body = createMultipartBody(
                "empty.pdf", new byte[0], MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody(String.class)
                .value(response -> assertJson(
                        "user/response/post_current_user_cv_empty_file.json", response));
    }

    @Test
    void testUploadCvUnsupportedMimeType() {
        MultiValueMap<String, Object> body = createMultipartBody(
                "notes.txt", "plain text".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody(String.class)
                .value(response -> assertJson(
                        "user/response/post_current_user_cv_unsupported_mime_type.json", response));
    }

    @Test
    void testCvEndpointsUnauthorizedWithoutToken() {
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        restClient.get()
                .uri(CURRENT_USER_CV_METADATA_ENDPOINT)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        restClient.delete()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        MultiValueMap<String, Object> body = createMultipartBody(
                "resume.pdf", SAMPLE_CV_CONTENT, MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testCvEndpointsForbiddenWithInvalidToken() {
        restClient.get()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(INVALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_forbidden.json", response));
        restClient.get()
                .uri(CURRENT_USER_CV_METADATA_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(INVALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_forbidden.json", response));
        restClient.delete()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(INVALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_forbidden.json", response));
        MultiValueMap<String, Object> body = createMultipartBody(
                "resume.pdf", SAMPLE_CV_CONTENT, MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(INVALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_forbidden.json", response));
    }

    private void uploadCv(String filename, byte[] content) {
        MultiValueMap<String, Object> body = createMultipartBody(filename, content, MediaType.APPLICATION_PDF);
        restClient.post()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);
    }

    private MultiValueMap<String, Object> createMultipartBody(String filename, byte[] content, MediaType mediaType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(mediaType);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);
        body.add("file", filePart);
        return body;
    }

    private void cleanStorageDirectory() {
        try {
            Path storagePath = Paths.get("target/test-storage");
            if (Files.exists(storagePath)) {
                try (var stream = Files.walk(storagePath)) {
                    stream.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(storagePath))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static Stream<Arguments> getCurrentUserTestCases() {
        return Stream.of(
                Arguments.of(
                        "Valid user token returns current user",
                        VALID_USER_JWT_PATH,
                        HttpStatus.OK,
                        "user/response/get_current_user.json"
                ),
                Arguments.of(
                        "User token without ID returns bad request",
                        NO_ID_USER_JWT_PATH,
                        HttpStatus.BAD_REQUEST,
                        "user/response/get_current_user_no_id.json"
                ),
                Arguments.of(
                        "User token without first name returns bad request",
                        NO_FIRST_NAME_USER_JWT_PATH,
                        HttpStatus.BAD_REQUEST,
                        "user/response/get_current_user_no_first_name.json"
                ),
                Arguments.of(
                        "User token without last name returns bad request",
                        NO_LAST_NAME_USER_JWT_PATH,
                        HttpStatus.BAD_REQUEST,
                        "user/response/get_current_user_no_last_name.json"
                ),
                Arguments.of(
                        "User token without email returns bad request",
                        NO_EMAIL_USER_JWT_PATH,
                        HttpStatus.BAD_REQUEST,
                        "user/response/get_current_user_no_email.json"
                ),
                Arguments.of(
                        "User token without phone number returns bad request",
                        NO_PHONE_NUMBER_USER_JWT_PATH,
                        HttpStatus.BAD_REQUEST,
                        "user/response/get_current_user_no_phone_number.json"
                ),
                Arguments.of(
                        "Invalid user token without required scope returns forbidden",
                        INVALID_USER_JWT_PATH,
                        HttpStatus.FORBIDDEN,
                        "user/response/get_current_user_forbidden.json"
                )
        );
    }

    @Test
    void testLookupUserUnauthorizedWithoutToken() {
        restClient.get()
                .uri(lookupUri("other.user@example.com"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testLookupUserForbiddenWithInvalidToken() {
        restClient.get()
                .uri(lookupUri("other.user@example.com"))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(INVALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_forbidden.json", response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/user/sql/remove_team_management_from_test_user.sql")
    void testLookupUserForbiddenWithoutTeamManagement() {
        restClient.get()
                .uri(lookupUri("other.user@example.com"))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/get_current_user_forbidden.json", response));
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("lookupUserSuccessTestCases")
    void testLookupUserSuccess(String description, String email) {
        restClient.get()
                .uri(lookupUri(email))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/lookup_user_other.json", response));
    }

    private static Stream<Arguments> lookupUserSuccessTestCases() {
        return Stream.of(
                Arguments.of("Exact email", "other.user@example.com"),
                Arguments.of("Email in different case", "Other.User@Example.COM")
        );
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("lookupUserNotFoundTestCases")
    void testLookupUserNotFound(String description, String email) {
        restClient.get()
                .uri(lookupUri(email))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("user/response/lookup_user_not_found.json", response));
    }

    private static Stream<Arguments> lookupUserNotFoundTestCases() {
        return Stream.of(
                Arguments.of("Unknown email", "nobody@example.com"),
                Arguments.of("Partial email is not a search", "other.user@example"),
                Arguments.of("LIKE wildcard is matched literally", "%@example.com"),
                Arguments.of("RSQL wildcard is matched literally", "*@example.com")
        );
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("lookupUserBadRequestTestCases")
    void testLookupUserBadRequest(String description, URI uri, String expectedResponsePath) {
        restClient.get()
                .uri(uri)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    private static Stream<Arguments> lookupUserBadRequestTestCases() {
        return Stream.of(
                Arguments.of("Missing email", URI.create(USER_LOOKUP_ENDPOINT),
                        "user/response/lookup_user_missing_email.json"),
                Arguments.of("Not an email", lookupUri("not-an-email"),
                        "user/response/lookup_user_invalid_email.json"),
                Arguments.of("Blank email", lookupUri("   "),
                        "user/response/lookup_user_invalid_email.json")
        );
    }

    private static URI lookupUri(String email) {
        return UriComponentsBuilder.fromPath(USER_LOOKUP_ENDPOINT)
                .queryParam("email", email)
                .encode()
                .build()
                .toUri();
    }

}
