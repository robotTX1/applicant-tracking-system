package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.APPLICATION_FEEDBACKS_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.APPLICATION_FEEDBACK_BY_ID_ENDPOINT;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.WiremockTest;
import hu.robottx.ats.annotation.DirtyTest;

class FeedbacksApiDelegateImplTest extends WiremockTest {

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/feedback/sql/load_test_feedbacks.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("getApplicationFeedbacksTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetApplicationFeedbacks(String description,
            String jwtPath,
            Long applicationId,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriWithSearchAndPageRequest(
                        APPLICATION_FEEDBACKS_ENDPOINT, applicationId, pageSize, pageNumber, sort, query));
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
    @Sql(scripts = "classpath:hu/robottx/ats/feedback/sql/load_test_feedbacks.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("getApplicationFeedbackByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetApplicationFeedbackById(String description,
            String jwtPath,
            Long applicationId,
            Long feedbackId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriByApplicationIdAndFeedbackId(
                        APPLICATION_FEEDBACK_BY_ID_ENDPOINT, applicationId, feedbackId));
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
    @Sql(scripts = "classpath:hu/robottx/ats/feedback/sql/load_test_feedbacks.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("saveApplicationFeedbackTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testSaveApplicationFeedback(String description,
            String jwtPath,
            Long applicationId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedLocation,
            String expectedResponsePath) {
        var requestSpec = restClient.post()
                .uri(uriById(APPLICATION_FEEDBACKS_ENDPOINT, applicationId))
                .body(loadText(requestPath));
        if (jwtPath != null) {
            requestSpec.headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)));
        }
        var responseSpec = requestSpec.exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedLocation == null) {
            responseSpec.expectHeader().doesNotExist(HttpHeaders.LOCATION);
        } else {
            responseSpec.expectHeader().location(expectedLocation);
        }
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/feedback/sql/load_test_feedbacks.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("updateApplicationFeedbackTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testUpdateApplicationFeedback(String description,
            String jwtPath,
            Long applicationId,
            Long feedbackId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.put()
                .uri(uriByApplicationIdAndFeedbackId(
                        APPLICATION_FEEDBACK_BY_ID_ENDPOINT, applicationId, feedbackId))
                .body(loadText(requestPath));
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
    @Sql(scripts = "classpath:hu/robottx/ats/feedback/sql/load_test_feedbacks.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("deleteApplicationFeedbackTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testDeleteApplicationFeedback(String description,
            String jwtPath,
            Long applicationId,
            Long feedbackId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.delete()
                .uri(uriByApplicationIdAndFeedbackId(
                        APPLICATION_FEEDBACK_BY_ID_ENDPOINT, applicationId, feedbackId));
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
    @Sql(scripts = "classpath:hu/robottx/ats/feedback/sql/load_test_feedbacks.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testDeleteApplicationFeedbackRemovesResource() {
        restClient.delete()
                .uri(uriByApplicationIdAndFeedbackId(APPLICATION_FEEDBACK_BY_ID_ENDPOINT, 6L, 7L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNoContent();

        restClient.get()
                .uri(uriByApplicationIdAndFeedbackId(APPLICATION_FEEDBACK_BY_ID_ENDPOINT, 6L, 7L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNotFound();
    }

    private static Stream<Arguments> getApplicationFeedbacksTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get all feedbacks for application 2 as OWNER returns HR and TECHNICAL feedbacks",
                        VALID_USER_JWT_PATH,
                        2L, null, null, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_2_all.json"
                ),
                Arguments.of(
                        "Get all feedbacks for application 7 as HR_MANAGER returns HR and TECHNICAL feedbacks",
                        VALID_USER_JWT_PATH,
                        7L, null, null, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_7_hr_manager.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 3 as TECHNICAL_INTERVIEWER returns TECHNICAL feedbacks only",
                        VALID_USER_JWT_PATH,
                        3L, null, null, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_3_technical_only.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 2 paginated page 0 size 2",
                        VALID_USER_JWT_PATH,
                        2L, 2, 0, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_2_page_0_size_2.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 2 paginated page 1 size 2",
                        VALID_USER_JWT_PATH,
                        2L, 2, 1, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_2_page_1_size_2.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 2 sorted by score ascending",
                        VALID_USER_JWT_PATH,
                        2L, null, null, "score,asc", null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_2_sort_score_asc.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 2 filtered by type HR as OWNER",
                        VALID_USER_JWT_PATH,
                        2L, null, null, null, "type==HR",
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_2_query_type_hr.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 3 filtered by type HR as TECHNICAL_INTERVIEWER returns empty",
                        VALID_USER_JWT_PATH,
                        3L, null, null, null, "type==HR",
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_empty.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 2 filtered by score >= 85 as OWNER",
                        VALID_USER_JWT_PATH,
                        2L, null, null, null, "score>=85",
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_2_query_score_gte_85.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 1 in HR_INTERVIEWED as OWNER",
                        VALID_USER_JWT_PATH,
                        1L, null, null, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_1.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 8 in HR_INTERVIEWED as TECHNICAL_INTERVIEWER returns 404",
                        VALID_USER_JWT_PATH,
                        8L, null, null, null, null,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_application_8_not_found.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 5 in TECHNICAL_INTERVIEW_SCHEDULED with no feedbacks returns empty",
                        VALID_USER_JWT_PATH,
                        5L, null, null, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_empty.json"
                ),
                Arguments.of(
                        "Get feedbacks for application 9 in OFFER as CO_OWNER returns feedbacks",
                        VALID_USER_JWT_PATH,
                        9L, null, null, null, null,
                        HttpStatus.OK,
                        "feedback/response/get_feedbacks_app_9_co_owner.json"
                ),
                Arguments.of(
                        "Get feedbacks for non-existent application returns 404",
                        VALID_USER_JWT_PATH,
                        999L, null, null, null, null,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_application_999_not_found.json"
                ),
                Arguments.of(
                        "Get feedbacks with invalid RSQL query returns 400",
                        VALID_USER_JWT_PATH,
                        2L, null, null, null, "invalidField==1",
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_invalid_query_or_sort.json"
                ),
                Arguments.of(
                        "Get feedbacks without token returns 401",
                        null,
                        2L, null, null, null, null,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Get feedbacks with invalid scope token returns 403",
                        INVALID_USER_JWT_PATH,
                        2L, null, null, null, null,
                        HttpStatus.FORBIDDEN,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getApplicationFeedbackByIdTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get HR feedback 2 for application 2 as OWNER",
                        VALID_USER_JWT_PATH,
                        2L, 2L,
                        HttpStatus.OK,
                        "feedback/response/get_feedback_2.json"
                ),
                Arguments.of(
                        "Get HR feedback 8 for application 7 as HR_MANAGER",
                        VALID_USER_JWT_PATH,
                        7L, 8L,
                        HttpStatus.OK,
                        "feedback/response/get_feedback_8.json"
                ),
                Arguments.of(
                        "Get HR feedback 5 for application 3 as TECHNICAL_INTERVIEWER returns 404",
                        VALID_USER_JWT_PATH,
                        3L, 5L,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_feedback_5_not_found.json"
                ),
                Arguments.of(
                        "Get TECHNICAL feedback 6 for application 3 as TECHNICAL_INTERVIEWER",
                        VALID_USER_JWT_PATH,
                        3L, 6L,
                        HttpStatus.OK,
                        "feedback/response/get_feedback_6.json"
                ),
                Arguments.of(
                        "Get feedback 11 for application 9 as CO_OWNER",
                        VALID_USER_JWT_PATH,
                        9L, 11L,
                        HttpStatus.OK,
                        "feedback/response/get_feedback_11.json"
                ),
                Arguments.of(
                        "Get feedback belonging to another application returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 2L,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_feedback_2_not_found.json"
                ),
                Arguments.of(
                        "Get non-existent feedback returns 404",
                        VALID_USER_JWT_PATH,
                        2L, 999L,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_feedback_999_not_found.json"
                ),
                Arguments.of(
                        "Get feedback for application 8 in HR_INTERVIEWED as TECHNICAL_INTERVIEWER returns 404",
                        VALID_USER_JWT_PATH,
                        8L, 10L,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_application_8_not_found.json"
                ),
                Arguments.of(
                        "Get feedback without token returns 401",
                        null,
                        2L, 2L,
                        HttpStatus.UNAUTHORIZED,
                        null
                )
        );
    }

    private static Stream<Arguments> saveApplicationFeedbackTestCases() {
        return Stream.of(
                Arguments.of(
                        "Create HR feedback in HR_INTERVIEWED stage as OWNER",
                        VALID_USER_JWT_PATH,
                        1L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.CREATED,
                        "/applications/1/feedbacks/51",
                        "feedback/response/post_feedback_app_1_owner.json"
                ),
                Arguments.of(
                        "Create HR feedback in HR_INTERVIEWED stage as HR_MANAGER",
                        VALID_USER_JWT_PATH,
                        11L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.CREATED,
                        "/applications/11/feedbacks/51",
                        "feedback/response/post_feedback_app_11_hr_manager.json"
                ),
                Arguments.of(
                        "Create TECHNICAL feedback in TECHNICAL_INTERVIEWED stage as CO_OWNER",
                        VALID_USER_JWT_PATH,
                        10L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.CREATED,
                        "/applications/10/feedbacks/51",
                        "feedback/response/post_feedback_app_10_co_owner.json"
                ),
                Arguments.of(
                        "Create TECHNICAL feedback in TECHNICAL_INTERVIEWED stage as TECHNICAL_INTERVIEWER",
                        VALID_USER_JWT_PATH,
                        3L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.CREATED,
                        "/applications/3/feedbacks/51",
                        "feedback/response/post_feedback_app_3_technical_interviewer.json"
                ),
                Arguments.of(
                        "Create duplicate HR feedback by same employee returns 409 Conflict",
                        VALID_USER_JWT_PATH,
                        6L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.CONFLICT,
                        null,
                        "feedback/response/error_duplicate_hr_feedback_app_6.json"
                ),
                Arguments.of(
                        "Create duplicate TECHNICAL feedback by same employee returns 409 Conflict",
                        VALID_USER_JWT_PATH,
                        2L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.CONFLICT,
                        null,
                        "feedback/response/error_duplicate_technical_feedback_app_2.json"
                ),
                Arguments.of(
                        "Create feedback in TECHNICAL_INTERVIEWED stage as HR_MANAGER returns 400",
                        VALID_USER_JWT_PATH,
                        7L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_no_permission_technical_feedback.json"
                ),
                Arguments.of(
                        "Create feedback in HR_INTERVIEWED stage as TECHNICAL_INTERVIEWER returns 404",
                        VALID_USER_JWT_PATH,
                        8L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "feedback/response/error_application_8_not_found.json"
                ),
                Arguments.of(
                        "Create feedback in TECHNICAL_INTERVIEW_SCHEDULED stage returns 400",
                        VALID_USER_JWT_PATH,
                        5L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_wrong_stage_for_creation.json"
                ),
                Arguments.of(
                        "Create feedback in APPLIED stage returns 400",
                        VALID_USER_JWT_PATH,
                        4L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_wrong_stage_for_creation.json"
                ),
                Arguments.of(
                        "Create feedback in OFFER stage returns 400",
                        VALID_USER_JWT_PATH,
                        9L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_wrong_stage_for_creation.json"
                ),
                Arguments.of(
                        "Create feedback with blank feedback returns 400",
                        VALID_USER_JWT_PATH,
                        1L,
                        "feedback/request/post_feedback_blank.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_empty_feedback.json"
                ),
                Arguments.of(
                        "Create feedback with missing feedback returns 400",
                        VALID_USER_JWT_PATH,
                        1L,
                        "feedback/request/post_feedback_missing_feedback.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_missing_feedback.json"
                ),
                Arguments.of(
                        "Create feedback with missing score returns 400",
                        VALID_USER_JWT_PATH,
                        1L,
                        "feedback/request/post_feedback_missing_score.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_missing_score.json"
                ),
                Arguments.of(
                        "Create feedback with score below 0 returns 400",
                        VALID_USER_JWT_PATH,
                        1L,
                        "feedback/request/post_feedback_score_below_zero.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_score_below_zero.json"
                ),
                Arguments.of(
                        "Create feedback with score above 100 returns 400",
                        VALID_USER_JWT_PATH,
                        1L,
                        "feedback/request/post_feedback_score_above_100.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "feedback/response/error_score_above_100.json"
                ),
                Arguments.of(
                        "Create feedback without token returns 401",
                        null,
                        1L,
                        "feedback/request/post_feedback_valid.json",
                        HttpStatus.UNAUTHORIZED,
                        null,
                        null
                )
        );
    }

    private static Stream<Arguments> updateApplicationFeedbackTestCases() {
        return Stream.of(
                Arguments.of(
                        "Update own HR feedback while application is in HR_INTERVIEWED stage",
                        VALID_USER_JWT_PATH,
                        6L, 7L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.OK,
                        "feedback/response/put_feedback_7_updated.json"
                ),
                Arguments.of(
                        "Update own TECHNICAL feedback while application is in TECHNICAL_INTERVIEWED stage",
                        VALID_USER_JWT_PATH,
                        2L, 3L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.OK,
                        "feedback/response/put_feedback_3_updated.json"
                ),
                Arguments.of(
                        "Update feedback authored by another employee returns 400",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_only_author_can_modify.json"
                ),
                Arguments.of(
                        "Update own HR feedback after application moved to TECHNICAL_INTERVIEWED returns 400",
                        VALID_USER_JWT_PATH,
                        7L, 8L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_modify_only_in_hr_interviewed.json"
                ),
                Arguments.of(
                        "Update own HR feedback after application moved to OFFER returns 400",
                        VALID_USER_JWT_PATH,
                        9L, 11L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_modify_only_in_hr_interviewed.json"
                ),
                Arguments.of(
                        "Update feedback with blank feedback returns 400",
                        VALID_USER_JWT_PATH,
                        6L, 7L,
                        "feedback/request/put_feedback_blank.json",
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_empty_feedback.json"
                ),
                Arguments.of(
                        "Update feedback with score above 100 returns 400",
                        VALID_USER_JWT_PATH,
                        6L, 7L,
                        "feedback/request/put_feedback_score_above_100.json",
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_score_above_100.json"
                ),
                Arguments.of(
                        "Update non-existent feedback returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 999L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_feedback_999_not_found.json"
                ),
                Arguments.of(
                        "Update feedback without token returns 401",
                        null,
                        1L, 1L,
                        "feedback/request/put_feedback_valid.json",
                        HttpStatus.UNAUTHORIZED,
                        null
                )
        );
    }

    private static Stream<Arguments> deleteApplicationFeedbackTestCases() {
        return Stream.of(
                Arguments.of(
                        "Delete own HR feedback while application is in HR_INTERVIEWED stage",
                        VALID_USER_JWT_PATH,
                        6L, 7L,
                        HttpStatus.NO_CONTENT,
                        null
                ),
                Arguments.of(
                        "Delete own TECHNICAL feedback while application is in TECHNICAL_INTERVIEWED stage",
                        VALID_USER_JWT_PATH,
                        2L, 3L,
                        HttpStatus.NO_CONTENT,
                        null
                ),
                Arguments.of(
                        "Delete feedback authored by another employee returns 400",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_only_author_can_modify.json"
                ),
                Arguments.of(
                        "Delete own HR feedback after application moved to TECHNICAL_INTERVIEWED returns 400",
                        VALID_USER_JWT_PATH,
                        7L, 8L,
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_modify_only_in_hr_interviewed.json"
                ),
                Arguments.of(
                        "Delete own HR feedback after application moved to OFFER returns 400",
                        VALID_USER_JWT_PATH,
                        9L, 11L,
                        HttpStatus.BAD_REQUEST,
                        "feedback/response/error_modify_only_in_hr_interviewed.json"
                ),
                Arguments.of(
                        "Delete non-existent feedback returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 999L,
                        HttpStatus.NOT_FOUND,
                        "feedback/response/error_feedback_999_not_found.json"
                ),
                Arguments.of(
                        "Delete feedback without token returns 401",
                        null,
                        1L, 1L,
                        HttpStatus.UNAUTHORIZED,
                        null
                )
        );
    }

    private static String uriByApplicationIdAndFeedbackId(String uri, Long applicationId, Long feedbackId) {
        return UriComponentsBuilder.fromUriString(uri)
                .buildAndExpand(applicationId, feedbackId)
                .toUriString();
    }

    private static URI uriWithSearchAndPageRequest(String uri, Long applicationId, Integer pageSize,
            Integer pageNumber, String sort, String query) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .queryParamIfPresent("pageNumber", Optional.ofNullable(pageNumber))
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .queryParamIfPresent("query", Optional.ofNullable(query))
                .buildAndExpand(applicationId)
                .toUri();
    }

}
