package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.JsonNode;
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

    private static final String ENDPOINT = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserV1Dto.SignUpRequest validRequest() {
        return new UserV1Dto.SignUpRequest(
            "loopers01", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> signUp(UserV1Dto.SignUpRequest request) {
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 회원가입하면, 200과 가입된 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenRequestIsValid() {
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = signUp(validRequest());

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김루퍼"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1995, 3, 21)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("looper@example.com")
            );
        }

        @DisplayName("회원가입 응답의 data 에는 password 필드 자체가 존재하지 않는다.")
        @Test
        void responseHasNoPasswordField() {
            ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(ENDPOINT, validRequest(), JsonNode.class);

            JsonNode data = response.getBody().get("data");
            assertThat(data.has("password")).isFalse();
        }

        @DisplayName("이미 사용 중인 로그인 ID로 가입하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenLoginIdIsDuplicated() {
            signUp(validRequest());

            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = signUp(validRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("형식에 맞지 않는 정보로 가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenRequestIsInvalid() {
            UserV1Dto.SignUpRequest invalidRequest = new UserV1Dto.SignUpRequest(
                "AB", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
            );

            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = signUp(invalidRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        private HttpHeaders authHeaders(String loginId, String loginPw) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.set("X-Loopers-LoginPw", loginPw);
            return headers;
        }

        private ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> requestMyInfo(HttpHeaders headers) {
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/me", HttpMethod.GET, new HttpEntity<>(headers), responseType);
        }

        @DisplayName("올바른 인증 헤더로 요청하면, 200과 내 정보를 반환한다. (이름은 마스킹)")
        @Test
        void returnsMyInfo_whenHeadersAreValid() {
            signUp(validRequest());

            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                requestMyInfo(authHeaders("loopers01", "Passw0rd!"));

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김루*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1995, 3, 21)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("looper@example.com")
            );
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            signUp(validRequest());

            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                requestMyInfo(authHeaders("loopers01", "WrongPass1!"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenHeadersAreMissing() {
            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                requestMyInfo(new HttpHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    class ChangePassword {

        private HttpHeaders authHeaders(String loginId, String loginPw) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.set("X-Loopers-LoginPw", loginPw);
            return headers;
        }

        private ResponseEntity<ApiResponse<Void>> requestChange(HttpHeaders headers, UserV1Dto.UpdatePasswordRequest body) {
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + "/password", HttpMethod.PUT, new HttpEntity<>(body, headers), responseType);
        }

        @DisplayName("올바른 헤더 + 유효한 요청이면, 200 으로 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenRequestIsValid() {
            signUp(validRequest());

            ResponseEntity<ApiResponse<Void>> response = requestChange(
                authHeaders("loopers01", "Passw0rd!"),
                new UserV1Dto.UpdatePasswordRequest("Passw0rd!", "NewPass1!")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("새 비밀번호 형식이 잘못되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenNewPasswordFormatIsInvalid() {
            signUp(validRequest());

            ResponseEntity<ApiResponse<Void>> response = requestChange(
                authHeaders("loopers01", "Passw0rd!"),
                new UserV1Dto.UpdatePasswordRequest("Passw0rd!", "short")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordMismatches() {
            signUp(validRequest());

            ResponseEntity<ApiResponse<Void>> response = requestChange(
                authHeaders("loopers01", "Passw0rd!"),
                new UserV1Dto.UpdatePasswordRequest("WrongPass1!", "NewPass1!")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenHeadersAreMissing() {
            ResponseEntity<ApiResponse<Void>> response = requestChange(
                new HttpHeaders(),
                new UserV1Dto.UpdatePasswordRequest("Passw0rd!", "NewPass1!")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
