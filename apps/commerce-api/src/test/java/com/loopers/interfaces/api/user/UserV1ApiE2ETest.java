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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Signup {
        @DisplayName("유효한 회원가입 요청이면, 비밀번호를 제외한 생성 회원 정보를 반환한다.")
        @Test
        void returnsCreatedUserInfo_whenSignupRequestIsValid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "user1234",
                "abc123!?",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "user@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(request.loginId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo(request.name()),
                () -> assertThat(response.getBody().data().birth()).isEqualTo(request.birth()),
                () -> assertThat(response.getBody().data().email()).isEqualTo(request.email())
            );
        }

        @DisplayName("중복 로그인 ID로 회원가입을 요청하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "user1234",
                "abc123!?",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "user@example.com"
            );
            testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }

        @DisplayName("요청 값이 유효하지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenSignupRequestIsInvalid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "abc",
                "short",
                "",
                LocalDate.now().plusDays(1),
                "invalid-email"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), voidResponseType());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {
        @DisplayName("인증 정보가 유효하면, 이름이 마스킹된 내 정보를 반환한다.")
        @Test
        void returnsMyInfoWithMaskedName_whenCredentialIsValid() {
            // arrange
            signup("user1234", "abc123!?", "홍길동");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(
                    "/api/v1/users/me",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    userResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user1234"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*")
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenCredentialHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, HttpEntity.EMPTY, voidResponseType());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            signup("user1234", "abc123!?", "홍길동");

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    "/api/v1/users/me",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "wrong123!")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/password")
    @Nested
    class ChangePassword {
        @DisplayName("현재 비밀번호와 새 비밀번호가 유효하면, 비밀번호를 변경한다.")
        @Test
        void changesPassword_whenRequestIsValid() {
            // arrange
            signup("user1234", "abc123!?", "홍길동");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("abc123!?", "new123!?");

            // act
            ResponseEntity<ApiResponse<Void>> changePasswordResponse =
                testRestTemplate.exchange(
                    "/api/v1/users/password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            ResponseEntity<ApiResponse<Void>> oldPasswordAuthResponse =
                testRestTemplate.exchange(
                    "/api/v1/users/me",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> newPasswordAuthResponse =
                testRestTemplate.exchange(
                    "/api/v1/users/me",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "new123!?")),
                    userResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(changePasswordResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(oldPasswordAuthResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(newPasswordAuthResponse.getStatusCode()).isEqualTo(HttpStatus.OK)
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
            // arrange
            signup("user1234", "abc123!?", "홍길동");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("abc123!?", "abc123!?");

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    "/api/v1/users/password",
                    HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private void signup(String loginId, String password, String name) {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            loginId,
            password,
            name,
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> userResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<Void>> voidResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
