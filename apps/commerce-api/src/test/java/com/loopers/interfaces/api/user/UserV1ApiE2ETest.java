package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
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

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_ME_PASSWORD = "/api/v1/users/me/password";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("유효한 회원 정보를 보내면, 201 CREATED 와 가입된 회원 정보를 반환한다.")
        @Test
        void returnsCreatedUser_whenValidRequestIsProvided() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김성호"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("loopers@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1993, 11, 3))
            );
        }

        @DisplayName("이미 가입된 로그인 ID 로 요청하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignUpRequest first = new UserV1Dto.SignUpRequest(
                "loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(first),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            UserV1Dto.SignUpRequest duplicate = new UserV1Dto.SignUpRequest(
                "loopers01", "Different!9999", "홍길동", LocalDate.of(1991, 1, 1), "other@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(duplicate), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호 정책에 맞지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordViolatesPolicy() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "loopers01", "short", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 헤더면, 200 OK 와 이름 마지막 글자가 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMyInfo_whenValidAuthHeadersAreProvided() {
            // arrange
            signUpUser("loopers01", "Loopers!2026");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = getMe("loopers01", "Loopers!2026");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김성*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1993, 11, 3)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("loopers@example.com")
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // act
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = getMe();

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            signUpUser("loopers01", "Loopers!2026");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = getMe("loopers01", "Wrong!9999");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 로그인 ID 면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLoginIdDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = getMe("ghost", "AnyPassword!1");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 인증 헤더와 현재/새 비밀번호를 보내면, 200 OK 응답을 받고 새 비밀번호로 로그인된다.")
        @Test
        void returnsOk_andEnablesLoginWithNewPassword_whenRequestIsValid() {
            // arrange
            String currentPassword = "Loopers!2026";
            String newPassword = "NewLoopers!9999";
            signUpUser("loopers01", currentPassword);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(currentPassword, newPassword);

            // act
            ResponseEntity<ApiResponse<Object>> response = changePassword("loopers01", currentPassword, request);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getMe("loopers01", newPassword).getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getMe("loopers01", currentPassword).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("바디의 현재 비밀번호가 저장된 값과 일치하지 않으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenBodyCurrentPasswordDoesNotMatch() {
            // arrange
            String currentPassword = "Loopers!2026";
            signUpUser("loopers01", currentPassword);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Wrong!9999", "NewLoopers!9999");

            // act
            ResponseEntity<ApiResponse<Object>> response = changePassword("loopers01", currentPassword, request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 정책에 어긋나면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordViolatesPolicy() {
            // arrange
            String currentPassword = "Loopers!2026";
            signUpUser("loopers01", currentPassword);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(currentPassword, "short");

            // act
            ResponseEntity<ApiResponse<Object>> response = changePassword("loopers01", currentPassword, request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String currentPassword = "Loopers!2026";
            signUpUser("loopers01", currentPassword);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(currentPassword, currentPassword);

            // act
            ResponseEntity<ApiResponse<Object>> response = changePassword("loopers01", currentPassword, request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // arrange
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Loopers!2026", "NewLoopers!9999");

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_ME_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<ApiResponse<Object>> changePassword(
        String loginId, String authPassword, UserV1Dto.ChangePasswordRequest body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, authPassword);
        return testRestTemplate.exchange(
            ENDPOINT_ME_PASSWORD, HttpMethod.PUT, new HttpEntity<>(body, headers),
            new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );
    }

    private void signUpUser(String loginId, String rawPassword) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            loginId, rawPassword, "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
        );
        testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
    }

    private ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> getMe(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return exchangeGetMe(new HttpEntity<>(headers));
    }

    private ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> getMe() {
        return exchangeGetMe(HttpEntity.EMPTY);
    }

    private ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> exchangeGetMe(HttpEntity<?> entity) {
        return testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, entity,
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {});
    }
}
