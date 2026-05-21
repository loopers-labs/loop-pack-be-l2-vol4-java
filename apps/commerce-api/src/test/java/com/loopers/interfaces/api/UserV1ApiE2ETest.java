package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.domain.user.UserRegistrationCommand;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_PASSWORD = "/api/v1/users/me/password";

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Pass1234!";
    private static final String VALID_NAME = "홍길동";
    private static final String VALID_BIRTH_DATE = "19900101";
    private static final String VALID_EMAIL = "hong@example.com";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", VALID_LOGIN_ID);
        headers.set("X-Loopers-LoginPw", VALID_PASSWORD);
        return headers;
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 회원가입하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequestIsGiven() {
            // arrange
            UserV1Dto.UserRegisterRequest body = new UserV1Dto.UserRegisterRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_USERS, HttpMethod.POST, new HttpEntity<>(body), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("이미 가입된 로그인 ID로 회원가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            UserV1Dto.UserRegisterRequest body = new UserV1Dto.UserRegisterRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_USERS, HttpMethod.POST, new HttpEntity<>(body), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("X-Loopers-LoginId, X-Loopers-LoginPw 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // arrange
            // (no headers)

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(null), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 로그인 ID로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLoginIdDoesNotExist() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexistent");
            headers.set("X-Loopers-LoginPw", VALID_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenCurrentPasswordIsWrong() {
            // arrange
            userService.register(new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            UserV1Dto.PasswordChangeRequest body = new UserV1Dto.PasswordChangeRequest("WrongPass1!", "NewPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(body, authHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
