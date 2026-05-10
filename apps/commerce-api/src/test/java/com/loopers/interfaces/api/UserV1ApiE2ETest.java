package com.loopers.interfaces.api;

import com.loopers.CommerceApiTestApplication;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    classes = CommerceApiTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/loopers_test",
        "datasource.mysql-jpa.main.username=application",
        "datasource.mysql-jpa.main.password=application"
    }
)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PASSWORD_HEADER = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class RegisterUser {

        @DisplayName("사용자 정보가 유효하면, 사용자를 등록하고 200 OK 응답을 받는다.")
        @Test
        void registersUser_whenRequestIsValid() {
            // arrange
            UserV1Dto.RegisterUserRequest request = new UserV1Dto.RegisterUserRequest(
                    "user1",
                    "Password1!",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    "user1@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo("user1"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1990, 1, 1)),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("user1@example.com"),
                    () -> assertThat(userJpaRepository.findByUserId("user1")).isPresent()
            );
        }

        @DisplayName("이메일 형식이 올바르지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            UserV1Dto.RegisterUserRequest request = new UserV1Dto.RegisterUserRequest(
                    "user1",
                    "Password1!",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("EMAIL_INVALID_FORMAT")
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetUser {

        @DisplayName("존재하는 로그인 ID와 비밀번호를 헤더로 주면, 사용자 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenLoginHeadersAreValid() {
            // arrange
            userJpaRepository.save(createUser());
            HttpHeaders headers = loginHeaders("user1", "Password1!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(null, headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo("user1"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1990, 1, 1)),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("user1@example.com")
            );
        }

        @DisplayName("존재하지 않는 로그인 ID를 헤더로 주면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenUserIdDoesNotExist() {
            // arrange
            HttpHeaders headers = loginHeaders("unknown", "Password1!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(null, headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("USER_NOT_FOUND")
            );
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호와 새 비밀번호가 유효하면, 비밀번호를 변경하고 200 OK 응답을 받는다.")
        @Test
        void changesPassword_whenRequestIsValid() {
            // arrange
            userJpaRepository.save(createUser());
            HttpHeaders headers = loginHeaders("user1", "Password1!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Password2!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo("user1"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("user1@example.com"),
                    () -> assertDoesNotThrow(() -> {
                        UserModel user = userJpaRepository.findByUserId("user1").orElseThrow();
                        user.verifyPassword("Password2!");
                    })
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            userJpaRepository.save(createUser());
            HttpHeaders headers = loginHeaders("user1", "Wrong1!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Password2!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("PASSWORD_MISMATCH")
            );
        }
    }

    private static HttpHeaders loginHeaders(String userId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LOGIN_ID_HEADER, userId);
        headers.set(LOGIN_PASSWORD_HEADER, password);
        return headers;
    }

    private static UserModel createUser() {
        return new UserModel(
                "user1",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user1@example.com"
        );
    }
}
