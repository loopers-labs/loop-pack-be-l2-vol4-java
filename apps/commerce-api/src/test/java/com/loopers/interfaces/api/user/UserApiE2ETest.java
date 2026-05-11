package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.E2ETest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@E2ETest
class UserApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_PASSWORD = "/api/v1/users/me/password";

    private static final String VALID_LOGIN_ID = "loopers01";
    private static final String VALID_PASSWORD = "Pass1234!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_EMAIL = "test@loopers.com";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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

    @DisplayName("POST /api/v1/users — 회원가입 시")
    @Nested
    class SignUp {

        @DisplayName("유효한 입력이면 200 OK 를 반환한다")
        @Test
        void returns200_whenInputIsValid() {
            // given
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // when
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                ENDPOINT_SIGNUP,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 409 CONFLICT 를 반환한다")
        @Test
        void returns409_whenLoginIdAlreadyExists() {
            // given
            userService.signUp(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                VALID_LOGIN_ID, "Other9876@", "김철수", LocalDate.of(2000, 3, 3), "other@loopers.com"
            );

            // when
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                ENDPOINT_SIGNUP,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호 규칙을 위반하면 400 BAD_REQUEST 를 반환한다")
        @Test
        void returns400_whenPasswordViolatesRule() {
            // given - 7자(8자 미만)
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                VALID_LOGIN_ID, "Short1!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // when
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                ENDPOINT_SIGNUP,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me — 내 정보 조회 시")
    @Nested
    class GetMyInfo {

        @DisplayName("올바른 인증 헤더로 요청하면 이름이 마스킹된 정보를 반환한다")
        @Test
        void returnsMaskedName_whenAuthenticated() {
            // given
            userService.signUp(VALID_LOGIN_ID, VALID_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // when
            ResponseEntity<ApiResponse<UserDto.MeResponse>> response = restTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(VALID_LOGIN_ID, VALID_PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(VALID_BIRTH_DATE)
            );
        }

        @DisplayName("인증 헤더가 누락되면 401 UNAUTHORIZED 를 반환한다")
        @Test
        void returns401_whenAuthHeadersAreMissing() {
            // given - 헤더 없음

            // when
            ResponseEntity<ApiResponse<UserDto.MeResponse>> response = restTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("틀린 비밀번호로 요청하면 401 UNAUTHORIZED 를 반환한다")
        @Test
        void returns401_whenPasswordIsWrong() {
            // given
            userService.signUp(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // when
            ResponseEntity<ApiResponse<UserDto.MeResponse>> response = restTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(VALID_LOGIN_ID, "Wrong9999@")),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password — 비밀번호 변경 시")
    @Nested
    class ChangePassword {

        @DisplayName("정상 변경 후 새 비밀번호로 인증되고 기존 비밀번호로는 401 을 반환한다")
        @Test
        void newPasswordAuthenticates_andOldFails_afterChange() {
            // given
            String newPassword = "NewPw9876@";
            userService.signUp(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            HttpHeaders headers = authHeaders(VALID_LOGIN_ID, VALID_PASSWORD);
            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest(VALID_PASSWORD, newPassword);

            // when
            ResponseEntity<ApiResponse<Void>> changeResponse = restTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<UserDto.MeResponse>> newAuthResponse = restTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(VALID_LOGIN_ID, newPassword)),
                new ParameterizedTypeReference<>() {}
            );
            ResponseEntity<ApiResponse<UserDto.MeResponse>> oldAuthResponse = restTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(VALID_LOGIN_ID, VALID_PASSWORD)),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(newAuthResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(oldAuthResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 401 UNAUTHORIZED 를 반환한다")
        @Test
        void returns401_whenCurrentPasswordIsWrong() {
            // given
            userService.signUp(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            HttpHeaders headers = authHeaders(VALID_LOGIN_ID, VALID_PASSWORD);
            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest("Wrong9999@", "NewPw9876@");

            // when
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                ENDPOINT_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
