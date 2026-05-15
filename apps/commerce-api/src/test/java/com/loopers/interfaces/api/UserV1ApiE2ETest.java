package com.loopers.interfaces.api;

import com.loopers.domain.user.UserService;
import com.loopers.domain.user.UserRegisterCommand;
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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);

    private final TestRestTemplate testRestTemplate;
    private final UserService userService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserService userService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userService = userService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Register {

        @DisplayName("올바른 정보를 모두 입력하면 201 Created 와 회원 정보를 반환한다.")
        @Test
        void returnsCreated_whenAllFieldsAreValid() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "user123", "Password1!", "홍길동", "19900101", "user@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user123"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("19900101"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("user@example.com")
            );
        }

        @DisplayName("이미 존재하는 loginId 로 가입하면 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "user123", "Password2@", "김철수", "19950202", "other@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호 형식이 올바르지 않으면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordIsInvalid() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "user123", "short", "홍길동", "19900101", "user@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("생년월일 형식이 올바르지 않으면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenBirthDateIsInvalid() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "user123", "Password1!", "홍길동", "1990-01-01", "user@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("올바른 인증 헤더를 보내면 200 OK 와 내 정보를 반환한다.")
        @Test
        void returnsMe_whenCredentialsAreValid() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            HttpHeaders headers = authHeaders("user123", "Password1!");
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user123"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("19900101"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("user@example.com")
            );
        }

        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeaderIsMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            HttpHeaders headers = authHeaders("user123", "WrongPass1!");
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면 200 OK 를 반환한다.")
        @Test
        void returnsOk_whenPasswordChangeIsSuccessful() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                "Password1!", "NewPass2@"
            );

            // act
            HttpHeaders headers = authHeaders("user123", "Password1!");
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("현재 비밀번호가 틀리면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                "WrongPass1!", "NewPass2@"
            );

            // act
            HttpHeaders headers = authHeaders("user123", "Password1!");
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeaderIsMissing() {
            // arrange
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                "Password1!", "NewPass2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
