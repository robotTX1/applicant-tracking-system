package hu.robottx.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;

import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_APPLICATIONS_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_APPLICATION_BY_ID_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_APPLICATION_CV_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_APPLICATION_CV_METADATA_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_CV_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_APPLICATIONS_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_APPLICATION_BY_ID_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_APPLICATION_CV_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_APPLICATION_CV_METADATA_ENDPOINT;

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

class ApplicationsApiDelegateImplTest extends WiremockTest {

    private static final byte[] SAMPLE_CV_CONTENT = "%PDF-1.4 sample CV content".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CUSTOM_CV_CONTENT = "%PDF-1.4 custom CV content".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
        cleanStorageDirectory();
    }

    @Test
    @DirtyTest
    void testSaveApplicationWithUploadedCv() {
        MultiValueMap<String, Object> body = createApplicationMultipartBody(
                "application/request/post_application_job_5.json",
                "custom_cv.pdf",
                CUSTOM_CV_CONTENT,
                MediaType.APPLICATION_PDF
        );
        restClient.post()
                .uri(CURRENT_USER_APPLICATIONS_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectHeader().location(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, 1L))
                .expectBody(String.class)
                .value(response -> assertJson("application/response/post_application_with_file.json", response));
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_CV_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, disp ->
                        assertThat(disp).contains("filename=\"custom_cv.pdf\""))
                .expectBody(byte[].class)
                .isEqualTo(CUSTOM_CV_CONTENT);
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_CV_METADATA_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/get_application_cv_metadata_uploaded.json", response));
    }

    @Test
    @DirtyTest
    void testSaveApplicationWithDefaultCvCreatesIndependentCopy() {
        uploadDefaultCv("default_cv.pdf", SAMPLE_CV_CONTENT);
        MultiValueMap<String, Object> body = createApplicationMultipartBody(
                "application/request/post_application_job_5.json", null, null, null);
        restClient.post()
                .uri(CURRENT_USER_APPLICATIONS_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectHeader().location(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, 1L))
                .expectBody(String.class)
                .value(response -> assertJson("application/response/post_application_with_default_cv.json", response));
        restClient.delete()
                .uri(CURRENT_USER_CV_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_CV_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, disp ->
                        assertThat(disp).contains("filename=\"default_cv.pdf\""))
                .expectBody(byte[].class)
                .isEqualTo(SAMPLE_CV_CONTENT);
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_CV_METADATA_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/get_application_cv_metadata_copied.json", response));
    }

    @Test
    @DirtyTest
    void testSaveApplicationConflictWhenAlreadyApplied() {
        MultiValueMap<String, Object> body = createApplicationMultipartBody(
                "application/request/post_application_job_5.json",
                "custom_cv.pdf",
                CUSTOM_CV_CONTENT,
                MediaType.APPLICATION_PDF
        );
        restClient.post()
                .uri(CURRENT_USER_APPLICATIONS_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);
        restClient.post()
                .uri(CURRENT_USER_APPLICATIONS_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/conflict_already_applied_job_5.json", response));
    }

    @DirtyTest
    @MethodSource("saveApplicationErrorTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testSaveApplicationErrors(String description,
            String jwtPath,
            String requestPath,
            String filename,
            byte[] fileContent,
            MediaType fileMediaType,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        MultiValueMap<String, Object> body = createApplicationMultipartBody(
                requestPath, filename, fileContent, fileMediaType);
        var requestSpec = restClient.post()
                .uri(CURRENT_USER_APPLICATIONS_ENDPOINT)
                .body(body);
        if (jwtPath != null) {
            requestSpec.headers(headers -> {
                headers.addAll(createAuthorizedHeaders(jwtPath));
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            });
        } else {
            requestSpec.headers(headers -> headers.setContentType(MediaType.MULTIPART_FORM_DATA));
        }
        var responseSpec = requestSpec.exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/application/sql/setup_user_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("getCurrentUserApplicationsTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetCurrentUserApplications(String description,
            String jwtPath,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriWithApplicationSearchAndPageRequest(
                        CURRENT_USER_APPLICATIONS_ENDPOINT, pageSize, pageNumber, sort, query));
        if (jwtPath != null) {
            requestSpec.headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)));
        }
        var responseSpec = requestSpec.exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/application/sql/setup_user_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("getCurrentUserApplicationByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetCurrentUserApplicationById(String description,
            String jwtPath,
            Long applicationId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, applicationId));
        if (jwtPath != null) {
            requestSpec.headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)));
        }
        var responseSpec = requestSpec.exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @Test
    @DirtyTest
    void testDeleteCurrentUserApplication() {
        MultiValueMap<String, Object> body = createApplicationMultipartBody(
                "application/request/post_application_job_5.json",
                "custom_cv.pdf",
                CUSTOM_CV_CONTENT,
                MediaType.APPLICATION_PDF
        );
        restClient.post()
                .uri(CURRENT_USER_APPLICATIONS_ENDPOINT)
                .headers(headers -> {
                    headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH));
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED);
        restClient.delete()
                .uri(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_1.json", response));
        restClient.delete()
                .uri(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_1.json", response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/application/sql/setup_user_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testCannotAccessOrDeleteOtherUsersApplication() {
        restClient.delete()
                .uri(uriById(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT, 3L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_3.json", response));
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_CV_ENDPOINT, 3L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_3.json", response));
        restClient.get()
                .uri(uriById(CURRENT_USER_APPLICATION_CV_METADATA_ENDPOINT, 3L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_3.json", response));
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/application/sql/setup_job_listing_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("getJobListingApplicationsTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetJobListingApplications(String description,
            String jwtPath,
            Long jobListingId,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriWithJobListingApplicationSearchAndPageRequest(
                        JOB_LISTING_APPLICATIONS_ENDPOINT, jobListingId, pageSize, pageNumber, sort, query));
        if (jwtPath != null) {
            requestSpec.headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)));
        }
        var responseSpec = requestSpec.exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @Test
    @DirtyTest
    @Sql(scripts = {
            "classpath:hu/robottx/ats/application/sql/setup_job_listing_applications.sql",
            "classpath:hu/robottx/ats/application/sql/add_co_owner_company_4.sql"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testGetJobListingApplicationsAsCoOwner() {
        restClient.get()
                .uri(uriById(JOB_LISTING_APPLICATIONS_ENDPOINT, 5L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/get_job_listing_5_applications_co_owner.json", response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = {
            "classpath:hu/robottx/ats/application/sql/setup_job_listing_applications.sql",
            "classpath:hu/robottx/ats/application/sql/set_user_technical_interviewer_company_1.sql"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testTechnicalInterviewerOnlySeesTechnicalStageApplications() throws IOException {
        Path cvPath = Paths.get("target/test-storage/applications/third-user/cv2.pdf");
        Files.createDirectories(cvPath.getParent());
        Files.write(cvPath, SAMPLE_CV_CONTENT);

        restClient.get()
                .uri(uriById(JOB_LISTING_APPLICATIONS_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/get_job_listing_1_applications_technical_only.json", response));
        restClient.get()
                .uri(uriByJobListingAndApplicationId(JOB_LISTING_APPLICATION_BY_ID_ENDPOINT, 1L, 2L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/get_job_listing_1_application_2.json", response));
        restClient.get()
                .uri(uriByJobListingAndApplicationId(JOB_LISTING_APPLICATION_CV_METADATA_ENDPOINT, 1L, 2L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "application/response/get_job_listing_1_application_2_cv_metadata.json", response));
        restClient.get()
                .uri(uriByJobListingAndApplicationId(JOB_LISTING_APPLICATION_CV_ENDPOINT, 1L, 2L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, disp ->
                        assertThat(disp).contains("filename=\"third_java_cv.pdf\""))
                .expectBody(byte[].class)
                .isEqualTo(SAMPLE_CV_CONTENT);
        restClient.get()
                .uri(uriByJobListingAndApplicationId(JOB_LISTING_APPLICATION_BY_ID_ENDPOINT, 1L, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_1.json", response));
        restClient.get()
                .uri(uriByJobListingAndApplicationId(JOB_LISTING_APPLICATION_CV_ENDPOINT, 1L, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_1.json", response));
        restClient.get()
                .uri(uriByJobListingAndApplicationId(JOB_LISTING_APPLICATION_CV_METADATA_ENDPOINT, 1L, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .value(response -> assertJson("application/response/not_found_application_1.json", response));
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/application/sql/setup_job_listing_applications.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("getJobListingApplicationByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetJobListingApplicationById(String description,
            String jwtPath,
            Long jobListingId,
            Long applicationId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriByJobListingAndApplicationId(
                        JOB_LISTING_APPLICATION_BY_ID_ENDPOINT, jobListingId, applicationId));
        if (jwtPath != null) {
            requestSpec.headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)));
        }
        var responseSpec = requestSpec.exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    private void uploadDefaultCv(String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        body.add("file", new HttpEntity<>(resource, partHeaders));
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

    private MultiValueMap<String, Object> createApplicationMultipartBody(
            String requestPath, String filename, byte[] fileContent, MediaType fileMediaType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("application", new HttpEntity<>(loadText(requestPath), jsonHeaders));
        if (filename != null && fileContent != null && fileMediaType != null) {
            ByteArrayResource resource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(fileMediaType);
            body.add("file", new HttpEntity<>(resource, fileHeaders));
        }
        return body;
    }

    private static URI uriWithApplicationSearchAndPageRequest(
            String uri, Integer pageSize, Integer pageNumber, String sort, String query) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .queryParamIfPresent("pageNumber", Optional.ofNullable(pageNumber))
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .queryParamIfPresent("query", Optional.ofNullable(query))
                .build()
                .toUri();
    }

    private static URI uriWithJobListingApplicationSearchAndPageRequest(
            String uri, Long jobListingId, Integer pageSize, Integer pageNumber, String sort, String query) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .queryParamIfPresent("pageNumber", Optional.ofNullable(pageNumber))
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .queryParamIfPresent("query", Optional.ofNullable(query))
                .buildAndExpand(jobListingId)
                .toUri();
    }

    private static String uriByJobListingAndApplicationId(String uri, Long jobListingId, Long applicationId) {
        return UriComponentsBuilder.fromUriString(uri)
                .buildAndExpand(jobListingId, applicationId)
                .toUriString();
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

    private static Stream<Arguments> saveApplicationErrorTestCases() {
        return Stream.of(
                Arguments.of(
                        "Missing file and no default CV returns bad request",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_5.json",
                        null,
                        null,
                        null,
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_no_cv.json"
                ),
                Arguments.of(
                        "Employee applying to own company job listing returns bad request",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_1_own_company.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_own_company.json"
                ),
                Arguments.of(
                        "Applying to DRAFT job listing returns not found",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_6_draft.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_job_listing_6.json"
                ),
                Arguments.of(
                        "Applying to CLOSED job listing returns not found",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_7_closed.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_job_listing_7.json"
                ),
                Arguments.of(
                        "Applying to non-existent job listing returns not found",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_999_not_found.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_job_listing_999.json"
                ),
                Arguments.of(
                        "Missing jobListingId returns bad request",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_missing_job_listing_id.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_missing_job_listing_id.json"
                ),
                Arguments.of(
                        "Empty CV file returns bad request",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_5.json",
                        "empty.pdf",
                        new byte[0],
                        MediaType.APPLICATION_PDF,
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_empty_file.json"
                ),
                Arguments.of(
                        "CV file exceeding 5 MB returns bad request",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_5.json",
                        "large.pdf",
                        new byte[5 * 1024 * 1024 + 1024],
                        MediaType.APPLICATION_PDF,
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_file_too_large.json"
                ),
                Arguments.of(
                        "Unsupported file MIME type returns bad request",
                        VALID_USER_JWT_PATH,
                        "application/request/post_application_job_5.json",
                        "resume.txt",
                        "plain text".getBytes(StandardCharsets.UTF_8),
                        MediaType.TEXT_PLAIN,
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_unsupported_mime_type.json"
                ),
                Arguments.of(
                        "Unauthenticated request returns unauthorized",
                        null,
                        "application/request/post_application_job_5.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Invalid scope token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        "application/request/post_application_job_5.json",
                        "custom_cv.pdf",
                        CUSTOM_CV_CONTENT,
                        MediaType.APPLICATION_PDF,
                        HttpStatus.FORBIDDEN,
                        "application/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getCurrentUserApplicationsTestCases() {
        return Stream.of(
                Arguments.of(
                        "Returns only current user's applications",
                        VALID_USER_JWT_PATH,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.OK,
                        "application/response/get_user_applications_all.json"
                ),
                Arguments.of(
                        "Filter current user's applications by status using RSQL",
                        VALID_USER_JWT_PATH,
                        null,
                        null,
                        null,
                        "status==HR_INTERVIEW_SCHEDULED",
                        HttpStatus.OK,
                        "application/response/get_user_applications_filtered_status.json"
                ),
                Arguments.of(
                        "Paginate and sort current user's applications",
                        VALID_USER_JWT_PATH,
                        1,
                        0,
                        "id,desc",
                        null,
                        HttpStatus.OK,
                        "application/response/get_user_applications_paginated.json"
                ),
                Arguments.of(
                        "Filtering by hidden aiScore field on user endpoint returns bad request",
                        VALID_USER_JWT_PATH,
                        null,
                        null,
                        null,
                        "aiScore>=80",
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_invalid_query.json"
                ),
                Arguments.of(
                        "Invalid RSQL query returns bad request",
                        VALID_USER_JWT_PATH,
                        null,
                        null,
                        null,
                        "invalidField==value",
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_invalid_query.json"
                ),
                Arguments.of(
                        "Unauthenticated request returns unauthorized",
                        null,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Invalid scope token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.FORBIDDEN,
                        "application/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getCurrentUserApplicationByIdTestCases() {
        return Stream.of(
                Arguments.of(
                        "Returns own application by ID",
                        VALID_USER_JWT_PATH,
                        1L,
                        HttpStatus.OK,
                        "application/response/get_user_application_1.json"
                ),
                Arguments.of(
                        "Other user's application returns not found",
                        VALID_USER_JWT_PATH,
                        3L,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_application_3.json"
                ),
                Arguments.of(
                        "Non-existent application returns not found",
                        VALID_USER_JWT_PATH,
                        999L,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_application_999.json"
                ),
                Arguments.of(
                        "Unauthenticated request returns unauthorized",
                        null,
                        1L,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Invalid scope token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        HttpStatus.FORBIDDEN,
                        "application/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getJobListingApplicationsTestCases() {
        return Stream.of(
                Arguments.of(
                        "OWNER sees all applications in all stages for job listing 1",
                        VALID_USER_JWT_PATH,
                        1L,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.OK,
                        "application/response/get_job_listing_1_applications_all.json"
                ),
                Arguments.of(
                        "OWNER can filter job listing applications by aiScore using RSQL",
                        VALID_USER_JWT_PATH,
                        1L,
                        null,
                        null,
                        null,
                        "aiScore>=90",
                        HttpStatus.OK,
                        "application/response/get_job_listing_1_applications_technical_only.json"
                ),
                Arguments.of(
                        "HR_MANAGER sees applications for closed job listing 4 in company 3",
                        VALID_USER_JWT_PATH,
                        4L,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.OK,
                        "application/response/get_job_listing_4_applications_hr_manager.json"
                ),
                Arguments.of(
                        "Non-employee cannot view applications for company 4's job listing 5",
                        VALID_USER_JWT_PATH,
                        5L,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_job_listing_5.json"
                ),
                Arguments.of(
                        "Non-existent job listing returns not found",
                        VALID_USER_JWT_PATH,
                        999L,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_job_listing_999.json"
                ),
                Arguments.of(
                        "Invalid RSQL query returns bad request",
                        VALID_USER_JWT_PATH,
                        1L,
                        null,
                        null,
                        null,
                        "invalidField==123",
                        HttpStatus.BAD_REQUEST,
                        "application/response/bad_request_invalid_query.json"
                ),
                Arguments.of(
                        "Unauthenticated request returns unauthorized",
                        null,
                        1L,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Invalid scope token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        null,
                        null,
                        null,
                        null,
                        HttpStatus.FORBIDDEN,
                        "application/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getJobListingApplicationByIdTestCases() {
        return Stream.of(
                Arguments.of(
                        "OWNER can view application in APPLIED stage",
                        VALID_USER_JWT_PATH,
                        1L,
                        1L,
                        HttpStatus.OK,
                        "application/response/get_job_listing_1_application_1.json"
                ),
                Arguments.of(
                        "OWNER can view application in TECHNICAL_INTERVIEW_SCHEDULED stage",
                        VALID_USER_JWT_PATH,
                        1L,
                        2L,
                        HttpStatus.OK,
                        "application/response/get_job_listing_1_application_2.json"
                ),
                Arguments.of(
                        "Application belonging to another job listing returns not found",
                        VALID_USER_JWT_PATH,
                        1L,
                        4L,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_application_4.json"
                ),
                Arguments.of(
                        "Non-employee requesting application on company 4 job listing returns job listing not found",
                        VALID_USER_JWT_PATH,
                        5L,
                        4L,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_job_listing_5.json"
                ),
                Arguments.of(
                        "Non-existent application ID returns not found",
                        VALID_USER_JWT_PATH,
                        1L,
                        999L,
                        HttpStatus.NOT_FOUND,
                        "application/response/not_found_application_999.json"
                ),
                Arguments.of(
                        "Unauthenticated request returns unauthorized",
                        null,
                        1L,
                        1L,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Invalid scope token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        1L,
                        HttpStatus.FORBIDDEN,
                        "application/response/forbidden.json"
                )
        );
    }

}
