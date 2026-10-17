package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.EMPLOYEES_ENDPOINT;
import static hu.robottx.ats.controller.RestControllerConstants.EMPLOYEE_BY_ID_ENDPOINT;

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

class EmployeesApiDelegateImplTest extends WiremockTest {

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
    }

    @MethodSource("getEmployeesTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetEmployees(String description,
            String jwtPath,
            Long companyId,
            Integer pageSize,
            Integer pageNumber,
            String sort,
            String query,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.get()
                .uri(uriWithEmployeeSearchAndPageRequest(
                        EMPLOYEES_ENDPOINT, companyId, pageSize, pageNumber, sort, query))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @MethodSource("getEmployeeByIdTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testGetEmployeeById(String description,
            String jwtPath,
            Long companyId,
            Long employeeId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.get()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, companyId, employeeId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @DirtyTest
    @MethodSource("saveEmployeeTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testSaveEmployee(String description,
            String jwtPath,
            Long companyId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedLocation,
            String expectedResponsePath) {
        var responseSpec = restClient.post()
                .uri(uriById(EMPLOYEES_ENDPOINT, companyId))
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
    @Sql(scripts = "classpath:hu/robottx/ats/company/sql/add_co_owner.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testSaveEmployeeAsCoOwner() {
        restClient.post()
                .uri(uriById(EMPLOYEES_ENDPOINT, 4L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("employee/request/post_employee_technical_interviewer.json"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 4L, 7L))
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/post_employee_co_owner_response.json",
                        response
                ));
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("updateEmployeeTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testUpdateEmployee(String description,
            String jwtPath,
            Long companyId,
            Long employeeId,
            String requestPath,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        restClient.put()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, companyId, employeeId))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(jwtPath)))
                .body(loadText(requestPath))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(String.class)
                .value(response -> assertJson(expectedResponsePath, response));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_co_owner_and_employee_to_company_4.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testUpdateEmployeeAsCoOwner() {
        restClient.put()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 4L, 7L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("employee/request/put_employee_technical_interviewer.json"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/put_employee_co_owner_response.json",
                        response
                ));
    }

    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @MethodSource("deleteEmployeeTestCases")
    @ParameterizedTest(name = "{index} - {0}")
    void testDeleteEmployee(String description,
            String jwtPath,
            Long companyId,
            Long employeeId,
            HttpStatus expectedStatus,
            String expectedResponsePath) {
        var responseSpec = restClient.delete()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, companyId, employeeId))
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
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_co_owner_and_employee_to_company_4.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testDeleteEmployeeAsCoOwner() {
        restClient.delete()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 4L, 7L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_inactive_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testReactivateEmployee() {
        restClient.post()
                .uri(uriById(EMPLOYEES_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("employee/request/post_employee_reactivate.json"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/reactivate_employee_response.json",
                        response
                ));
        restClient.get()
                .uri(uriById(EMPLOYEES_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/post_reactivated_all_employees.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_inactive_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testGetInactiveEmployeeById() {
        restClient.get()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/get_employee_6_inactive.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_inactive_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testGetEmployeesFilteredByInactiveStatus() {
        restClient.get()
                .uri(uriWithEmployeeSearchAndPageRequest(
                        EMPLOYEES_ENDPOINT, 1L, null, null, null, "status==INACTIVE"))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/get_company_1_employees_inactive.json",
                        response
                ));
        restClient.get()
                .uri(uriById(EMPLOYEES_ENDPOINT, 1L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/get_company_1_employees.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/set_user_inactive_in_company_3.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testInactiveUserCannotAccessCompany() {
        restClient.get()
                .uri(uriById(EMPLOYEES_ENDPOINT, 3L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/company_3_not_found.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_inactive_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testUpdateInactiveEmployeeReturnsNotFound() {
        restClient.put()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .body(loadText("employee/request/put_employee.json"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/employee_6_not_found.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_inactive_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testDeleteInactiveEmployeeReturnsNotFound() {
        restClient.delete()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/employee_6_not_found.json",
                        response
                ));
    }

    @Test
    @DirtyTest
    @Sql(scripts = "classpath:hu/robottx/ats/employee/sql/add_employee_to_company_1.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void testDeleteAndVerifySoftDelete() {
        restClient.delete()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNoContent();
        restClient.get()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/get_employee_6_inactive.json",
                        response
                ));
        restClient.delete()
                .uri(uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L))
                .headers(headers -> headers.addAll(createAuthorizedHeaders(VALID_USER_JWT_PATH)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(response -> assertJson(
                        "employee/response/employee_6_not_found.json",
                        response
                ));
    }

    private static Stream<Arguments> getEmployeesTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get all employees of company 3",
                        VALID_USER_JWT_PATH,
                        3L, null, null, null, null,
                        HttpStatus.OK,
                        "employee/response/get_company_3_employees.json"
                ),
                Arguments.of(
                        "Get employees filtered by role",
                        VALID_USER_JWT_PATH,
                        3L, null, null, null, "role==HR_MANAGER",
                        HttpStatus.OK,
                        "employee/response/get_company_3_employees_filtered_role.json"
                ),
                Arguments.of(
                        "Get employees filtered by email",
                        VALID_USER_JWT_PATH,
                        3L, null, null, null, "email==\"other.user@example.com\"",
                        HttpStatus.OK,
                        "employee/response/get_company_3_employees_filtered_email.json"
                ),
                Arguments.of(
                        "Get employees paginated and sorted",
                        VALID_USER_JWT_PATH,
                        3L, 1, 0, "id,asc", null,
                        HttpStatus.OK,
                        "employee/response/get_company_3_employees_paginated.json"
                ),
                Arguments.of(
                        "Get employees with invalid query returns bad request",
                        VALID_USER_JWT_PATH,
                        3L, null, null, null, "invalid==test",
                        HttpStatus.BAD_REQUEST,
                        "employee/response/invalid_query.json"
                ),
                Arguments.of(
                        "Get employees by non-employee returns not found",
                        VALID_USER_JWT_PATH,
                        4L, null, null, null, null,
                        HttpStatus.NOT_FOUND,
                        "employee/response/company_not_found.json"
                ),
                Arguments.of(
                        "Get employees with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        3L, null, null, null, null,
                        HttpStatus.FORBIDDEN,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> getEmployeeByIdTestCases() {
        return Stream.of(
                Arguments.of(
                        "Get employee 3 of company 3",
                        VALID_USER_JWT_PATH,
                        3L, 3L,
                        HttpStatus.OK,
                        "employee/response/get_employee_3.json"
                ),
                Arguments.of(
                        "Get employee 4 of company 3",
                        VALID_USER_JWT_PATH,
                        3L, 4L,
                        HttpStatus.OK,
                        "employee/response/get_employee_4.json"
                ),
                Arguments.of(
                        "Get non-existent employee returns not found",
                        VALID_USER_JWT_PATH,
                        3L, 99L,
                        HttpStatus.NOT_FOUND,
                        "employee/response/employee_not_found.json"
                ),
                Arguments.of(
                        "Get employee from different company returns not found",
                        VALID_USER_JWT_PATH,
                        3L, 1L,
                        HttpStatus.NOT_FOUND,
                        "employee/response/employee_1_not_found.json"
                ),
                Arguments.of(
                        "Get employee by non-employee returns not found",
                        VALID_USER_JWT_PATH,
                        4L, 5L,
                        HttpStatus.NOT_FOUND,
                        "employee/response/company_not_found.json"
                ),
                Arguments.of(
                        "Get employee with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        3L, 3L,
                        HttpStatus.FORBIDDEN,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> saveEmployeeTestCases() {
        return Stream.of(
                Arguments.of(
                        "Save employee by owner",
                        VALID_USER_JWT_PATH,
                        1L,
                        "employee/request/post_employee.json",
                        HttpStatus.CREATED,
                        uriByCompanyIdAndEmployeeId(EMPLOYEE_BY_ID_ENDPOINT, 1L, 6L),
                        "employee/response/post_employee_response.json"
                ),
                Arguments.of(
                        "Save employee with OWNER role returns bad request",
                        VALID_USER_JWT_PATH,
                        1L,
                        "employee/request/post_employee_owner_role.json",
                        HttpStatus.BAD_REQUEST,
                        null,
                        "employee/response/bad_request_cannot_assign_owner.json"
                ),
                Arguments.of(
                        "Save employee that already exists returns conflict",
                        VALID_USER_JWT_PATH,
                        1L,
                        "employee/request/post_employee_already_employee.json",
                        HttpStatus.CONFLICT,
                        null,
                        "employee/response/conflict_already_employee.json"
                ),
                Arguments.of(
                        "Save employee with non-existent user returns not found",
                        VALID_USER_JWT_PATH,
                        1L,
                        "employee/request/post_employee_user_not_found.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "employee/response/user_not_found.json"
                ),
                Arguments.of(
                        "Save employee by non-owner returns not found",
                        VALID_USER_JWT_PATH,
                        3L,
                        "employee/request/post_employee.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "employee/response/company_3_not_found.json"
                ),
                Arguments.of(
                        "Save employee by non-employee returns not found",
                        VALID_USER_JWT_PATH,
                        4L,
                        "employee/request/post_employee.json",
                        HttpStatus.NOT_FOUND,
                        null,
                        "employee/response/company_not_found.json"
                ),
                Arguments.of(
                        "Save employee with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L,
                        "employee/request/post_employee.json",
                        HttpStatus.FORBIDDEN,
                        null,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> updateEmployeeTestCases() {
        return Stream.of(
                Arguments.of(
                        "Update employee by owner",
                        VALID_USER_JWT_PATH,
                        1L, 6L,
                        "employee/request/put_employee.json",
                        HttpStatus.OK,
                        "employee/response/put_employee_response.json"
                ),
                Arguments.of(
                        "Update company owner role returns bad request",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        "employee/request/put_employee.json",
                        HttpStatus.BAD_REQUEST,
                        "employee/response/bad_request_cannot_update_owner.json"
                ),
                Arguments.of(
                        "Update employee to OWNER role returns bad request",
                        VALID_USER_JWT_PATH,
                        1L, 6L,
                        "employee/request/put_employee_owner_role.json",
                        HttpStatus.BAD_REQUEST,
                        "employee/response/bad_request_cannot_assign_owner.json"
                ),
                Arguments.of(
                        "Update employee by non-owner returns not found",
                        VALID_USER_JWT_PATH,
                        3L, 4L,
                        "employee/request/put_employee.json",
                        HttpStatus.NOT_FOUND,
                        "employee/response/company_3_not_found.json"
                ),
                Arguments.of(
                        "Update non-existent employee returns not found",
                        VALID_USER_JWT_PATH,
                        1L, 99L,
                        "employee/request/put_employee.json",
                        HttpStatus.NOT_FOUND,
                        "employee/response/employee_not_found.json"
                ),
                Arguments.of(
                        "Update employee with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L, 6L,
                        "employee/request/put_employee.json",
                        HttpStatus.FORBIDDEN,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static Stream<Arguments> deleteEmployeeTestCases() {
        return Stream.of(
                Arguments.of(
                        "Delete employee by owner",
                        VALID_USER_JWT_PATH,
                        1L, 6L,
                        HttpStatus.NO_CONTENT,
                        null
                ),
                Arguments.of(
                        "Delete company owner returns bad request",
                        VALID_USER_JWT_PATH,
                        1L, 1L,
                        HttpStatus.BAD_REQUEST,
                        "employee/response/bad_request_cannot_remove_owner.json"
                ),
                Arguments.of(
                        "Delete employee by non-owner returns not found",
                        VALID_USER_JWT_PATH,
                        3L, 4L,
                        HttpStatus.NOT_FOUND,
                        "employee/response/company_3_not_found.json"
                ),
                Arguments.of(
                        "Delete non-existent employee returns not found",
                        VALID_USER_JWT_PATH,
                        1L, 99L,
                        HttpStatus.NOT_FOUND,
                        "employee/response/employee_not_found.json"
                ),
                Arguments.of(
                        "Delete employee with invalid token returns forbidden",
                        INVALID_USER_JWT_PATH,
                        1L, 6L,
                        HttpStatus.FORBIDDEN,
                        "employee/response/forbidden.json"
                )
        );
    }

    private static String uriByCompanyIdAndEmployeeId(String uri, Long companyId, Long employeeId) {
        return UriComponentsBuilder.fromUriString(uri)
                .buildAndExpand(companyId, employeeId)
                .toUriString();
    }

    private static URI uriWithEmployeeSearchAndPageRequest(String uri, Long companyId, Integer pageSize,
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
