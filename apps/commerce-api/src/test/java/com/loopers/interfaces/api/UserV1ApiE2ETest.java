package com.loopers.interfaces.api;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";
    private static final String ENDPOINT_GET_MY_INFO = "/api/v1/users/profile";
    private static final String ENDPOINT_UPDATE_PASSWORD = "/api/v1/users/profile/password";

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_EMAIL = "test@example.com";

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

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
        return headers;
    }

    private void saveDefaultUser() {
        userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
    }

    // ── POST /api/v1/users ────────────────────────────────────────────────────

    @DisplayName("POST /api/v1/users")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 요청하면, 201 Created와 가입된 회원 정보를 반환한다.")
        @Test
        void returnsCreatedUser_whenAllFieldsAreValid() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_NAME),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 요청하면, 409 Conflict를 반환한다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            saveDefaultUser();
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, "OtherPass1!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("로그인 ID에 특수문자가 포함되어 있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "user@123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenEmailIsInvalid() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되어 있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDate() {
            // arrange - birthDate: 1990-01-15 → "19900115"
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, "Ab!19900115", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────────────

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 헤더로 요청하면, 200 OK와 회원 정보(마스킹된 이름 포함)를 반환한다.")
        @Test
        void returnsUserInfo_whenAuthHeadersAreValid() {
            // arrange
            saveDefaultUser();

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID 헤더로 요청하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "nonexistent");
            headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("비밀번호 헤더가 일치하지 않으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            saveDefaultUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            headers.set(HEADER_LOGIN_PW, "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── PATCH /api/v1/users/me/password ──────────────────────────────────────

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 인증 헤더와 새 비밀번호로 요청하면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenPasswordChangedSuccessfully() {
            // arrange
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                VALID_PASSWORD, "NewPass2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                "WrongPass1!", "NewPass2@"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                VALID_PASSWORD, VALID_PASSWORD
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되어 있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange - birthDate: 1990-01-15 → "19900115"
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                VALID_PASSWORD, "Ab!19900115"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_UPDATE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
