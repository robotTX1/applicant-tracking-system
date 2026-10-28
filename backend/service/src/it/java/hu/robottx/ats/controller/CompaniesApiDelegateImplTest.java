package hu.robottx.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;

import static hu.robottx.ats.controller.RestControllerConstants.COMPANIES_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.COMPANY_BY_ID_ENDPOINT;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

import hu.robottx.ats.WiremockTest;
import hu.robottx.ats.annotation.DirtyTest;
import hu.robottx.ats.domain.EmailTemplateType;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.repository.EmailTemplateRepository;

class CompaniesApiDelegateImplTest extends WiremockTest {

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
    }

    @MethodSource("getCompaniesTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetCompanies(String description,
            String jwtPath,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            Boolean isEmployee,
            CompanyRole role,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.get()
                .uri(uriWithSearchAndPageRequest(
                        COMPANIES_ENDPOINT, pageSize, pageNumber, sort, query, isEmployee, role))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @MethodSource("getCompanyByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetCompanyById(String description,
            String jwtPath,
            Long companyId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.get()
                .uri(uriById(COMPANY_BY_ID_ENDPOINT, companyId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @DirtyTest
    @MethodSource("saveCompanyTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testSaveCompany(String description,
            String jwtPath,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedLocation,
            String expectedResponsePath) {
        var responseSpec = restClient.post()
                .uri(COMPANIES_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .body(loadText(requestPath))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedLocation == null) {
            responseSpec.expectHeader().doesNotExist(HttpHeaders.LOCATION);
        } else {
            responseSpec.expectHeader().location(expectedLocation);
        }
        responseSpec.expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/reach_company_limit.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testSaveCompanyWhenResourceLimitIsReached() {
        restClient.post()
                .uri(COMPANIES_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("company/request/post_company.json"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "company/response/post_company_resource_limit_reached.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    void testSaveCompanyGeneratesDefaultEmailTemplates() {
        restClient.post()
                .uri(COMPANIES_ENDPOINT)
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("company/request/post_company.json"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location(uriById(COMPANY_BY_ID_ENDPOINT, 5L));
        assertThat(emailTemplateRepository.countByCompanyCompanyIdAndType(5L, EmailTemplateType.OFFER))
                .isEqualTo(1);
        assertThat(emailTemplateRepository.countByCompanyCompanyIdAndType(5L, EmailTemplateType.DECLINE))
                .isEqualTo(1);
    }

    @DirtyTest
    @MethodSource("updateCompanyTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testUpdateCompany(String description,
            String jwtPath,
            Long companyId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.put()
                .uri(uriById(COMPANY_BY_ID_ENDPOINT, companyId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .body(loadText(requestPath))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testUpdateCompanyAsCoOwner() {
        restClient.put()
                .uri(uriById(COMPANY_BY_ID_ENDPOINT, 4L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("company/request/put_company_co_owner.json"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "company/response/put_company_co_owner.json",
                        response
                ));
    }

    @DirtyTest
    @MethodSource("deleteCompanyTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testDeleteCompany(String description,
            String jwtPath,
            Long companyId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var responseSpec = restClient.delete()
                .uri(uriById(COMPANY_BY_ID_ENDPOINT, companyId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedResponsePath != null) {
            responseSpec.expectBody(String.class)
                    .value(response -> assertJson(expectedResponsePath, response));
        }
    }

    private static Stream<Arguments> getCompaniesTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get all companies without query and sort",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, null, null,
                        HttpStatus.OK,
                        "company/response/get_all_companies.json"
                ),
                Arguments.of(
                        "Get companies filtered by name",
                        VALID_USER_JWT_PATH,
                        null, null, null, "name==\"TechCorp\"", null, null,
                        HttpStatus.OK,
                        "company/response/get_companies_by_name.json"
                ),
                Arguments.of(
                        "Get companies with RSQL ilike query and descending ID sort",
                        VALID_USER_JWT_PATH,
                        null, null, "id,desc", "description=ilike=\"cloud\"", null, null,
                        HttpStatus.OK,
                        "company/response/get_companies_with_query_and_sort.json"
                ),
                Arguments.of(
                        "Get companies with pagination and ascending name sort",
                        VALID_USER_JWT_PATH,
                        2, 0, "name,asc", null, null, null,
                        HttpStatus.OK,
                        "company/response/get_companies_paginated.json"
                ),
                Arguments.of(
                        "Get companies where current user is an employee",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, true, null,
                        HttpStatus.OK,
                        "company/response/get_companies_is_employee_true.json"
                ),
                Arguments.of(
                        "Get companies where current user is not an employee",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, false, null,
                        HttpStatus.OK,
                        "company/response/get_companies_is_employee_false.json"
                ),
                Arguments.of(
                        "Get companies where current user has OWNER role",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, null, CompanyRole.OWNER,
                        HttpStatus.OK,
                        "company/response/get_companies_role_owner.json"
                ),
                Arguments.of(
                        "Get companies where current user has HR_MANAGER role",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, true, CompanyRole.HR_MANAGER,
                        HttpStatus.OK,
                        "company/response/get_companies_role_hr_manager.json"
                ),
                Arguments.of(
                        "Get companies where current user has TECHNICAL_INTERVIEWER role returns empty list",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, null, CompanyRole.TECHNICAL_INTERVIEWER,
                        HttpStatus.OK,
                        "company/response/get_companies_empty.json"
                ),
                Arguments.of(
                        "Get companies with isEmployee false and role specified returns bad request",
                        VALID_USER_JWT_PATH,
                        null, null, null, null, false, CompanyRole.OWNER,
                        HttpStatus.BAD_REQUEST,
                        "company/response/get_companies_invalid_employee_role_combination.json"
                ),
                Arguments.of(
                        "Get companies with unknown RSQL property returns bad request",
                        VALID_USER_JWT_PATH,
                        null, null, null, "unknownProperty==test", null, null,
                        HttpStatus.BAD_REQUEST,
                        "company/response/get_companies_invalid_query.json"
                ),
                Arguments.of(
                        "Get companies with invalid RSQL syntax returns bad request",
                        VALID_USER_JWT_PATH,
                        null, null, null, "name==", null, null,
                        HttpStatus.BAD_REQUEST,
                        "company/response/get_companies_invalid_query.json"
                ),
                Arguments.of(
                        "Get companies with page size less than minimum returns bad request",
                        VALID_USER_JWT_PATH,
                        0, 0, null, null, null, null,
                        HttpStatus.BAD_REQUEST,
                        "company/response/get_companies_invalid_page_size_min.json"
                ),
                Arguments.of(
                        "Get companies with page size more than maximum returns bad request",
                        VALID_USER_JWT_PATH,
                        101, 0, null, null, null, null,
                        HttpStatus.BAD_REQUEST,
                        "company/response/get_companies_invalid_page_size_max.json"
                ),
                Arguments.of(
                        "Get companies with negative page number returns bad request",
                        VALID_USER_JWT_PATH,
                        20, -1, null, null, null, null,
                        HttpStatus.BAD_REQUEST,
                        "company/response/get_companies_invalid_page_number.json"
                ),
                Arguments.of(
                        "Get companies with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        null, null, null, null, null, null,
                        HttpStatus.FORBIDDEN,
                        "company/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getCompanyByIdTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get existing company by ID",
                        VALID_USER_JWT_PATH,
                        1L,
                        HttpStatus.OK,
                        "company/response/get_company_by_id.json"
                ),
                Arguments.of(
                        "Get non-existent company by ID returns not found",
                        VALID_USER_JWT_PATH,
                        99L,
                        HttpStatus.NOT_FOUND,
                        "company/response/get_company_not_found.json"
                ),
                Arguments.of(
                        "Get company by ID with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        HttpStatus.FORBIDDEN,
                        "company/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> saveCompanyTestCases() {
        return Stream.of(
                Arguments.of(
                        "Create company with valid details",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company.json",
                        HttpStatus.CREATED,
                        uriById(COMPANY_BY_ID_ENDPOINT, 5L),
                        "company/response/post_company.json"
                ),
                Arguments.of(
                        "Create company with duplicate name returns conflict",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company_conflict.json",
                        HttpStatus.CONFLICT,
                        null,
                        "company/response/post_company_conflict.json"
                ),
                Arguments.of(
                        "Create company without name returns bad request",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company_no_name.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "company/response/post_company_no_name.json"
                ),
                Arguments.of(
                        "Create company with empty name returns bad request",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company_empty_name.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "company/response/post_company_empty_name.json"
                ),
                Arguments.of(
                        "Create company without founding date returns bad request",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company_no_founding_date.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "company/response/post_company_no_founding_date.json"
                ),
                Arguments.of(
                        "Create company without website URL returns bad request",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company_no_website_url.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "company/response/post_company_no_website_url.json"
                ),
                Arguments.of(
                        "Create company without description returns bad request",
                        VALID_USER_JWT_PATH,
                        "company/request/post_company_no_description.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "company/response/post_company_no_description.json"
                ),
                Arguments.of(
                        "Create company with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        "company/request/post_company.json",
                        HttpStatus.FORBIDDEN,
                        null,
                        "company/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> updateCompanyTestCases() {
        return Stream.of(
                Arguments.of(
                        "Update own company",
                        VALID_USER_JWT_PATH,
                        1L,
                        "company/request/put_company.json",
                        HttpStatus.OK,
                        "company/response/put_company.json"
                ),
                Arguments.of(
                        "Update own company to existing company name returns conflict",
                        VALID_USER_JWT_PATH,
                        1L,
                        "company/request/put_company_conflict.json",
                        HttpStatus.CONFLICT,
                        "company/response/put_company_conflict.json"
                ),
                Arguments.of(
                        "Update company where user is hiring manager returns not found",
                        VALID_USER_JWT_PATH,
                        3L,
                        "company/request/put_company.json",
                        HttpStatus.NOT_FOUND,
                        "company/response/put_company_not_owner.json"
                ),
                Arguments.of(
                        "Update other user's company returns not found",
                        VALID_USER_JWT_PATH,
                        4L,
                        "company/request/put_company.json",
                        HttpStatus.NOT_FOUND,
                        "company/response/put_company_other_users.json"
                ),
                Arguments.of(
                        "Update non-existent company returns not found",
                        VALID_USER_JWT_PATH,
                        99L,
                        "company/request/put_company.json",
                        HttpStatus.NOT_FOUND,
                        "company/response/put_company_non_existent.json"
                ),
                Arguments.of(
                        "Update company without name returns bad request",
                        VALID_USER_JWT_PATH,
                        1L,
                        "company/request/put_company_no_name.json",
                        HttpStatus.BAD_REQUEST,
                        "company/response/post_company_no_name.json"
                ),
                Arguments.of(
                        "Update company with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        "company/request/put_company.json",
                        HttpStatus.FORBIDDEN,
                        "company/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> deleteCompanyTestCases() {
        return Stream.of(
                Arguments.of(
                        "Delete own company",
                        VALID_USER_JWT_PATH,
                        1L,
                        HttpStatus.NO_CONTENT,
                        null
                ),
                Arguments.of(
                        "Delete company where user is hiring manager returns not found",
                        VALID_USER_JWT_PATH,
                        3L,
                        HttpStatus.NOT_FOUND,
                        "company/response/put_company_not_owner.json"
                ),
                Arguments.of(
                        "Delete other user's company returns not found",
                        VALID_USER_JWT_PATH,
                        4L,
                        HttpStatus.NOT_FOUND,
                        "company/response/put_company_other_users.json"
                ),
                Arguments.of(
                        "Delete non-existent company returns not found",
                        VALID_USER_JWT_PATH,
                        99L,
                        HttpStatus.NOT_FOUND,
                        "company/response/put_company_non_existent.json"
                ),
                Arguments.of(
                        "Delete company with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        HttpStatus.FORBIDDEN,
                        "company/response/forbidden.json"
                )
        );
    }

}
