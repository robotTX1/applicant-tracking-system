package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTINGS_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_BY_ID_ENDPOINT;

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

class JobListingsApiDelegateImplTest extends WiremockTest {

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
    }

    @MethodSource("getJobListingsTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetJobListings(String description,
            String jwtPath,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var responseSpec = restClient.get()
                .uri(uriWithJobListingSearchAndPageRequest(
                        JOB_LISTINGS_ENDPOINT, pageSize, pageNumber, sort, query))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @MethodSource("getJobListingByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetJobListingById(String description,
            String jwtPath,
            Long jobListingId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var responseSpec = restClient.get()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, jobListingId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testGetJobListingByIdAsCoOwner() {
        restClient.get()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "joblisting/response/get_job_listing_6.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(statements = "INSERT INTO employees (employee_id, user_id, company_id, role, status, assigned_date, "
            + "created_at, updated_at) VALUES (10, 'test-user', 4, 'TECHNICAL_INTERVIEWER', 'ACTIVE', "
            + "'2026-01-01 10:00:00', '2026-01-01 10:00:00', '2026-01-01 10:00:00')",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testGetJobListingByIdAsTechnicalInterviewerSeesInternalFieldsOnPublished() {
        restClient.get()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, 5L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "joblisting/response/get_job_listing_5_employee.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("saveJobListingTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testSaveJobListing(String description,
            String jwtPath,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedLocation,
            String expectedResponsePath) {
        var responseSpec = restClient.post()
                .uri(JOB_LISTINGS_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .body(loadText(requestPath))
                .exchange()
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

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testSaveJobListingAsCoOwner() {
        restClient.post()
                .uri(JOB_LISTINGS_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("joblisting/request/post_job_listing_co_owner.json"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location(uriById(JOB_LISTING_BY_ID_ENDPOINT, 8L))
                .expectBody(String.class)
                .value(response -> assertJson(
                        "joblisting/response/post_job_listing_co_owner_response.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("updateJobListingTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testUpdateJobListing(String description,
            String jwtPath,
            Long jobListingId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var responseSpec = restClient.put()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, jobListingId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .body(loadText(requestPath))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testUpdateJobListingAsCoOwner() {
        restClient.put()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, 5L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("joblisting/request/put_job_listing_co_owner.json"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "joblisting/response/put_job_listing_co_owner_response.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("deleteJobListingTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testDeleteJobListing(String description,
            String jwtPath,
            Long jobListingId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var responseSpec = restClient.delete()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, jobListingId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testDeleteJobListingAsCoOwner() {
        restClient.delete()
                .uri(uriById(JOB_LISTING_BY_ID_ENDPOINT, 5L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNoContent();
    }

    private static URI uriWithJobListingSearchAndPageRequest(String uri, Integer pageSize, Integer pageNumber,
            String sort, String query) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .queryParamIfPresent("pageNumber", Optional.ofNullable(pageNumber))
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .queryParamIfPresent("query", Optional.ofNullable(query))
                .build().toUri();
    }

    private static Stream<Arguments> getJobListingsTestCases() {
        return Stream.of(
                Arguments.of("Get all visible job listings",
                        VALID_USER_JWT_PATH, null, null, null, null,
                        HttpStatus.OK, "joblisting/response/get_all_job_listings.json"),
                Arguments.of("Get job listings filtered by title",
                        VALID_USER_JWT_PATH, null, null, null, "title=ilike=\"Developer\"",
                        HttpStatus.OK, "joblisting/response/get_job_listings_filtered_by_title.json"),
                Arguments.of("Get job listings filtered by status DRAFT",
                        VALID_USER_JWT_PATH, null, null, null, "status==DRAFT",
                        HttpStatus.OK, "joblisting/response/get_job_listings_filtered_by_status_draft.json"),
                Arguments.of("Get job listings filtered by company",
                        VALID_USER_JWT_PATH, null, null, null, "companyId==1",
                        HttpStatus.OK, "joblisting/response/get_job_listings_filtered_by_company.json"),
                Arguments.of("Get job listings paginated",
                        VALID_USER_JWT_PATH, 2, 0, null, null,
                        HttpStatus.OK, "joblisting/response/get_job_listings_paginated.json"),
                Arguments.of("Get job listings with invalid query",
                        VALID_USER_JWT_PATH, null, null, null, "invalidProperty==1",
                        HttpStatus.BAD_REQUEST, "joblisting/response/bad_request_invalid_query.json"),
                Arguments.of("Get job listings filtering by restricted aiReviewEnabled returns bad request",
                        VALID_USER_JWT_PATH, null, null, null, "aiReviewEnabled==true",
                        HttpStatus.BAD_REQUEST, "joblisting/response/bad_request_invalid_query.json"),
                Arguments.of("Get job listings filtering by restricted aiReviewMinScore returns bad request",
                        VALID_USER_JWT_PATH, null, null, null, "aiReviewMinScore>=70",
                        HttpStatus.BAD_REQUEST, "joblisting/response/bad_request_invalid_query.json"),
                Arguments.of("Get job listings with invalid sort",
                        VALID_USER_JWT_PATH, null, null, "invalidProperty,asc", null,
                        HttpStatus.BAD_REQUEST, "joblisting/response/bad_request_invalid_query.json"),
                Arguments.of("Get job listings with invalid page size min",
                        VALID_USER_JWT_PATH, 0, null, null, null,
                        HttpStatus.BAD_REQUEST, "company/response/get_companies_invalid_page_size_min.json"),
                Arguments.of("Get job listings with invalid page size max",
                        VALID_USER_JWT_PATH, 101, null, null, null,
                        HttpStatus.BAD_REQUEST, "company/response/get_companies_invalid_page_size_max.json"),
                Arguments.of("Get job listings with invalid page number",
                        VALID_USER_JWT_PATH, null, -1, null, null,
                        HttpStatus.BAD_REQUEST, "company/response/get_companies_invalid_page_number.json"),
                Arguments.of("Get job listings with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH, null, null, null, null,
                        HttpStatus.FORBIDDEN, "company/response/forbidden.json")
        );
    }

    private static Stream<Arguments> getJobListingByIdTestCases() {
        return Stream.of(
                Arguments.of("Get published job listing 1 of company 1 (as owner)",
                        VALID_USER_JWT_PATH, 1L,
                        HttpStatus.OK, "joblisting/response/get_job_listing_1.json"),
                Arguments.of("Get draft job listing 2 of company 1 (as owner)",
                        VALID_USER_JWT_PATH, 2L,
                        HttpStatus.OK, "joblisting/response/get_job_listing_2.json"),
                Arguments.of("Get closed job listing 4 of company 3 (as HR manager)",
                        VALID_USER_JWT_PATH, 4L,
                        HttpStatus.OK, "joblisting/response/get_job_listing_4.json"),
                Arguments.of("Get published job listing 5 of company 4 (as non-employee)",
                        VALID_USER_JWT_PATH, 5L,
                        HttpStatus.OK, "joblisting/response/get_job_listing_5.json"),
                Arguments.of("Get draft job listing 6 of company 4 (as non-employee)",
                        VALID_USER_JWT_PATH, 6L,
                        HttpStatus.NOT_FOUND, "joblisting/response/not_found_job_listing_6.json"),
                Arguments.of("Get closed job listing 7 of company 4 (as non-employee)",
                        VALID_USER_JWT_PATH, 7L,
                        HttpStatus.NOT_FOUND, "joblisting/response/not_found_job_listing_7.json"),
                Arguments.of("Get non-existent job listing 999",
                        VALID_USER_JWT_PATH, 999L,
                        HttpStatus.NOT_FOUND, "joblisting/response/not_found_job_listing_999.json"),
                Arguments.of("Get job listing with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH, 1L,
                        HttpStatus.FORBIDDEN, "company/response/forbidden.json")
        );
    }

    private static Stream<Arguments> saveJobListingTestCases() {
        return Stream.of(
                Arguments.of("Save job listing as owner with default status",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_default_status.json",
                        HttpStatus.CREATED,
                        uriById(JOB_LISTING_BY_ID_ENDPOINT, 8L),
                        "joblisting/response/post_job_listing_response.json"),
                Arguments.of("Save job listing as HR manager with PUBLISHED status",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_published.json",
                        HttpStatus.CREATED,
                        uriById(JOB_LISTING_BY_ID_ENDPOINT, 8L),
                        "joblisting/response/post_job_listing_published_response.json"),
                Arguments.of("Save job listing as non-manager of company 4",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_not_manager.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "joblisting/response/not_found_company_4.json"),
                Arguments.of("Save job listing with CLOSED status",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_closed.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "joblisting/response/bad_request_closed_status.json"),
                Arguments.of("Save job listing with non-existent email template returns 404",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_template_not_found.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "emailtemplate/response/not_found_email_template_999.json"),
                Arguments.of("Save job listing with wrong email template type returns 400",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_wrong_template_type.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "joblisting/response/bad_request_wrong_template_type.json"),
                Arguments.of("Save job listing with AI review enabled but missing min score returns 400",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_missing_min_score.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "joblisting/response/bad_request_ai_review_min_score_required.json"),
                Arguments.of("Save job listing with validation error",
                        VALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_missing_title.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        null),
                Arguments.of("Save job listing with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        "joblisting/request/post_job_listing_default_status.json",
                        HttpStatus.FORBIDDEN,
                        null,
                        "company/response/forbidden.json")
        );
    }

    private static Stream<Arguments> updateJobListingTestCases() {
        return Stream.of(
                Arguments.of("Update job listing as owner",
                        VALID_USER_JWT_PATH, 1L,
                        "joblisting/request/put_job_listing.json",
                        HttpStatus.OK,
                        "joblisting/response/put_job_listing_response.json"),
                Arguments.of("Update job listing as HR manager",
                        VALID_USER_JWT_PATH, 4L,
                        "joblisting/request/put_job_listing_close.json",
                        HttpStatus.OK,
                        "joblisting/response/put_job_listing_close_response.json"),
                Arguments.of("Update job listing as non-manager of company 4",
                        VALID_USER_JWT_PATH, 5L,
                        "joblisting/request/put_job_listing.json",
                        HttpStatus.NOT_FOUND,
                        "joblisting/response/not_found_job_listing_5.json"),
                Arguments.of("Update non-existent job listing",
                        VALID_USER_JWT_PATH, 999L,
                        "joblisting/request/put_job_listing.json",
                        HttpStatus.NOT_FOUND,
                        "joblisting/response/not_found_job_listing_999.json"),
                Arguments.of("Update job listing with validation error",
                        VALID_USER_JWT_PATH, 1L,
                        "joblisting/request/put_job_listing_missing_title.json",
                        HttpStatus.BAD_REQUEST,
                        null),
                Arguments.of("Update job listing with AI review enabled but missing min score returns 400",
                        VALID_USER_JWT_PATH, 1L,
                        "joblisting/request/put_job_listing_missing_min_score.json",
                        HttpStatus.BAD_REQUEST,
                        "joblisting/response/bad_request_ai_review_min_score_required.json"),
                Arguments.of("Update job listing with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH, 1L,
                        "joblisting/request/put_job_listing.json",
                        HttpStatus.FORBIDDEN,
                        "company/response/forbidden.json")
        );
    }

    private static Stream<Arguments> deleteJobListingTestCases() {
        return Stream.of(
                Arguments.of("Delete job listing as owner",
                        VALID_USER_JWT_PATH, 2L,
                        HttpStatus.NO_CONTENT, null),
                Arguments.of("Delete job listing as HR manager",
                        VALID_USER_JWT_PATH, 4L,
                        HttpStatus.NO_CONTENT, null),
                Arguments.of("Delete job listing as non-manager of company 4",
                        VALID_USER_JWT_PATH, 5L,
                        HttpStatus.NOT_FOUND,
                        "joblisting/response/not_found_job_listing_5.json"),
                Arguments.of("Delete non-existent job listing",
                        VALID_USER_JWT_PATH, 999L,
                        HttpStatus.NOT_FOUND,
                        "joblisting/response/not_found_job_listing_999.json"),
                Arguments.of("Delete job listing with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH, 1L,
                        HttpStatus.FORBIDDEN,
                        "company/response/forbidden.json")
        );
    }

}
