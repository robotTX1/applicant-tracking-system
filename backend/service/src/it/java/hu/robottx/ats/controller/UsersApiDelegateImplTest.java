package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_ENDPOINT;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import hu.robottx.ats.WiremockTest;
import hu.robottx.ats.annotation.DirtyTest;

class UsersApiDelegateImplTest extends WiremockTest {

    private static final String NO_ID_USER_JWT_PATH = "token/no-id-user.jwt";
    private static final String NO_FIRST_NAME_USER_JWT_PATH = "token/no-first-name-user.jwt";
    private static final String NO_LAST_NAME_USER_JWT_PATH = "token/no-last-name-user.jwt";
    private static final String NO_EMAIL_USER_JWT_PATH = "token/no-email-user.jwt";
    private static final String NO_PHONE_NUMBER_USER_JWT_PATH = "token/no-phone-number-user.jwt";

    @BeforeEach
    void setUp() {
        mockServer.resetAll();
        createDefaultExpectations();
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

}
