package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.EMAIL_TEMPLATES_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.EMAIL_TEMPLATE_BY_ID_ENDPOINT;

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

class EmailTemplatesApiDelegateImplTest extends WiremockTest {

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
    }

    @MethodSource("getEmailTemplatesTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetEmailTemplates(String description,
            String jwtPath,
            Long companyId,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriWithEmailTemplateSearchAndPageRequest(
                        EMAIL_TEMPLATES_ENDPOINT, companyId, pageSize, pageNumber, sort, query));
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

    @MethodSource("getEmailTemplateByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetEmailTemplateById(String description,
            String jwtPath,
            Long companyId,
            Long emailTemplateId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.get()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, companyId, emailTemplateId));
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
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testGetEmailTemplateByIdAsCoOwner() {
        restClient.get()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, 4L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "emailtemplate/response/get_email_template_6.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("saveEmailTemplateTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testSaveEmailTemplate(String description,
            String jwtPath,
            Long companyId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedLocation,
            String expectedResponsePath) {
        var requestSpec = restClient.post()
                .uri(uriById(EMAIL_TEMPLATES_ENDPOINT, companyId))
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

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testSaveEmailTemplateAsCoOwner() {
        restClient.post()
                .uri(uriById(EMAIL_TEMPLATES_ENDPOINT, 4L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("emailtemplate/request/post_email_template.json"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/companies/4/email-templates/7");
    }

    @Test
    @DirtyTest
    void testSaveEmailTemplateLimitReached() {
        for (int i = 0; i < 2; i++) {
            restClient.post()
                    .uri(uriById(EMAIL_TEMPLATES_ENDPOINT, 1L))
                    .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                    .body(loadText("emailtemplate/request/post_email_template.json"))
                    .exchange()
                    .expectStatus().isCreated();
        }
        restClient.post()
                .uri(uriById(EMAIL_TEMPLATES_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("emailtemplate/request/post_email_template.json"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "emailtemplate/response/bad_request_template_limit_reached.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("updateEmailTemplateTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testUpdateEmailTemplate(String description,
            String jwtPath,
            Long companyId,
            Long emailTemplateId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.put()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, companyId, emailTemplateId))
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

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testUpdateEmailTemplateAsCoOwner() {
        restClient.put()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, 4L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("emailtemplate/request/put_email_template.json"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DirtyTest
    void testUpdateEmailTemplateTypeLimitReached() {
        for (int i = 0; i < 2; i++) {
            restClient.post()
                    .uri(uriById(EMAIL_TEMPLATES_ENDPOINT, 1L))
                    .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                    .body(loadText("emailtemplate/request/post_email_template_decline.json"))
                    .exchange()
                    .expectStatus().isCreated();
        }
        // Now attempt to update OFFER template 1 to type DECLINE -> fails with limit reached
        restClient.put()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, 1L, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("emailtemplate/request/put_email_template_change_type.json"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "emailtemplate/response/bad_request_template_limit_reached_decline.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("deleteEmailTemplateTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testDeleteEmailTemplate(String description,
            String jwtPath,
            Long companyId,
            Long emailTemplateId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var requestSpec = restClient.delete()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, companyId, emailTemplateId));
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
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testDeleteEmailTemplateAsCoOwner() {
        restClient.delete()
                .uri(uriByCompanyIdAndTemplateId(EMAIL_TEMPLATE_BY_ID_ENDPOINT, 4L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNoContent();
    }

    private static Stream<Arguments> getEmailTemplatesTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get all email templates for company 1 as owner",
                        VALID_USER_JWT_PATH,
                        1L, null, null, null, null,
                        HttpStatus.OK,
                        "emailtemplate/response/get_all_email_templates_company_1.json"
                ),
                Arguments.of(
                        "Filter email templates by type OFFER",
                        VALID_USER_JWT_PATH,
                        1L, null, null, null, "type==OFFER",
                        HttpStatus.OK,
                        "emailtemplate/response/get_email_templates_filtered_by_type.json"
                ),
                Arguments.of(
                        "Filter email templates by subject",
                        VALID_USER_JWT_PATH,
                        1L, null, null, null, "subject=='Software Engineer Offer'",
                        HttpStatus.OK,
                        "emailtemplate/response/get_email_templates_filtered_by_subject.json"
                ),
                Arguments.of(
                        "Get email templates paginated",
                        VALID_USER_JWT_PATH,
                        1L, 1, 0, null, null,
                        HttpStatus.OK,
                        "emailtemplate/response/get_email_templates_paginated.json"
                ),
                Arguments.of(
                        "Get email templates with invalid query returns 400",
                        VALID_USER_JWT_PATH,
                        1L, null, null, null, "invalid==",
                        HttpStatus.BAD_REQUEST,
                        "emailtemplate/response/bad_request_invalid_query.json"
                ),
                Arguments.of(
                        "Get email templates for company where user has no access returns 404",
                        VALID_USER_JWT_PATH,
                        4L, null, null, null, null,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_company_4.json"
                ),
                Arguments.of(
                        "Get email templates for non-existent company returns 404",
                        VALID_USER_JWT_PATH,
                        999L, null, null, null, null,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_company_999.json"
                ),
                Arguments.of(
                        "Get email templates without token returns 401",
                        null,
                        1L, null, null, null, null,
                        HttpStatus.UNAUTHORIZED,
                        null
                ),
                Arguments.of(
                        "Get email templates with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L, null, null, null, null,
                        HttpStatus.FORBIDDEN,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getEmailTemplateByIdTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get email template 1 for company 1 as owner",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        HttpStatus.OK,
                        "emailtemplate/response/get_email_template_1.json"
                ),
                Arguments.of(
                        "Get email template 2 for company 1 as owner",
                        VALID_USER_JWT_PATH,
                        1L, 2L,
                        HttpStatus.OK,
                        "emailtemplate/response/get_email_template_2.json"
                ),
                Arguments.of(
                        "Get email template 4 for company 3 as HR manager",
                        VALID_USER_JWT_PATH,
                        3L, 4L,
                        HttpStatus.OK,
                        "emailtemplate/response/get_email_template_4.json"
                ),
                Arguments.of(
                        "Get non-existent email template returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 999L,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_email_template_999.json"
                ),
                Arguments.of(
                        "Get email template belonging to another company returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 4L,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_email_template_4.json"
                ),
                Arguments.of(
                        "Get email template where user has no access returns 404",
                        VALID_USER_JWT_PATH,
                        4L, 6L,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_company_4.json"
                ),
                Arguments.of(
                        "Get email template without token returns 401",
                        null,
                        1L, 1L,
                        HttpStatus.UNAUTHORIZED,
                        null
                )
        );
    }

    private static Stream<Arguments> saveEmailTemplateTestCases() {
        return Stream.of(
                Arguments.of(
                        "Save email template as owner",
                        VALID_USER_JWT_PATH,
                        1L,
                        "emailtemplate/request/post_email_template.json",
                        HttpStatus.CREATED,
                        "/companies/1/email-templates/7",
                        "emailtemplate/response/post_email_template_response.json"
                ),
                Arguments.of(
                        "Save email template as HR manager",
                        VALID_USER_JWT_PATH,
                        3L,
                        "emailtemplate/request/post_email_template.json",
                        HttpStatus.CREATED,
                        "/companies/3/email-templates/7",
                        null
                ),
                Arguments.of(
                        "Save email template with missing type returns 400",
                        VALID_USER_JWT_PATH,
                        1L,
                        "emailtemplate/request/post_email_template_missing_type.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        null
                ),
                Arguments.of(
                        "Save email template for company where user has no access returns 404",
                        VALID_USER_JWT_PATH,
                        4L,
                        "emailtemplate/request/post_email_template.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "emailtemplate/response/not_found_company_4.json"
                ),
                Arguments.of(
                        "Save email template without token returns 401",
                        null,
                        1L,
                        "emailtemplate/request/post_email_template.json",
                        HttpStatus.UNAUTHORIZED,
                        null,
                        null
                )
        );
    }

    private static Stream<Arguments> updateEmailTemplateTestCases() {
        return Stream.of(
                Arguments.of(
                        "Update email template as owner",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        "emailtemplate/request/put_email_template.json",
                        HttpStatus.OK,
                        "emailtemplate/response/put_email_template_response.json"
                ),
                Arguments.of(
                        "Update email template as HR manager",
                        VALID_USER_JWT_PATH,
                        3L, 4L,
                        "emailtemplate/request/put_email_template.json",
                        HttpStatus.OK,
                        null
                ),
                Arguments.of(
                        "Update email template with missing subject returns 400",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        "emailtemplate/request/put_email_template_missing_subject.json",
                        HttpStatus.BAD_REQUEST,
                        null
                ),
                Arguments.of(
                        "Update non-existent email template returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 999L,
                        "emailtemplate/request/put_email_template.json",
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_email_template_999.json"
                ),
                Arguments.of(
                        "Update email template in company where user has no access returns 404",
                        VALID_USER_JWT_PATH,
                        4L, 6L,
                        "emailtemplate/request/put_email_template.json",
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_company_4.json"
                ),
                Arguments.of(
                        "Update email template without token returns 401",
                        null,
                        1L, 1L,
                        "emailtemplate/request/put_email_template.json",
                        HttpStatus.UNAUTHORIZED,
                        null
                )
        );
    }

    private static Stream<Arguments> deleteEmailTemplateTestCases() {
        return Stream.of(
                Arguments.of(
                        "Delete email template as owner",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        HttpStatus.NO_CONTENT,
                        null
                ),
                Arguments.of(
                        "Delete email template as HR manager",
                        VALID_USER_JWT_PATH,
                        3L, 5L,
                        HttpStatus.NO_CONTENT,
                        null
                ),
                Arguments.of(
                        "Delete non-existent email template returns 404",
                        VALID_USER_JWT_PATH,
                        1L, 999L,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_email_template_999.json"
                ),
                Arguments.of(
                        "Delete email template in company where user has no access returns 404",
                        VALID_USER_JWT_PATH,
                        4L, 6L,
                        HttpStatus.NOT_FOUND,
                        "emailtemplate/response/not_found_company_4.json"
                ),
                Arguments.of(
                        "Delete email template without token returns 401",
                        null,
                        1L, 1L,
                        HttpStatus.UNAUTHORIZED,
                        null
                )
        );
    }

    private static String uriByCompanyIdAndTemplateId(String uri, Long companyId, Long emailTemplateId) {
        return UriComponentsBuilder.fromUriString(uri)
                .buildAndExpand(companyId, emailTemplateId)
                .toUriString();
    }

    private static URI uriWithEmailTemplateSearchAndPageRequest(String uri, Long companyId, Integer pageSize,
            Integer pageNumber, String sort, String query) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .queryParamIfPresent("pageNumber", Optional.ofNullable(pageNumber))
                .queryParamIfPresent("sort", Optional.ofNullable(sort))
                .queryParamIfPresent("query", Optional.ofNullable(query))
                .buildAndExpand(companyId)
                .toUri();
    }

}
