package com.loopers.interfaces.api;

import com.loopers.domain.user.Gender;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
    private static final String ENDPOINT_GET_MY_INFO = "/api/v1/users/me";

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
    class SignUp {

        @DisplayName("정상 가입 요청이면, 2xx 응답과 생성된 유저 정보를 반환한다.")
        @Test
        void returnsCreatedUser_whenRequestIsValid() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().id()).isPositive(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("tester01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().gender()).isEqualTo(Gender.M)
            );
        }

        @DisplayName("성별이 누락되면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenGenderIsMissing() {
            // arrange
            Map<String, Object> request = new HashMap<>();
            request.put("loginId", "tester01");
            request.put("password", "Password1!");
            request.put("name", "홍길동");
            request.put("birthDate", "1990-05-14");
            request.put("email", "test@example.com");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        @DisplayName("정상 헤더로 조회하면, 2xx 응답과 마지막 글자가 마스킹된 이름이 반환된다.")
        @Test
        void returnsMaskedUserInfo_whenAuthenticated() {
            // arrange
            userJpaRepository.save(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M
            ));

            // act
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester01");
            headers.set("X-Loopers-LoginPw", "Password1!");
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
                ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("tester01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-05-14"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 로그인 ID 로 조회하면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenLoginIdNotExists() {
            // act
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexistent");
            headers.set("X-Loopers-LoginPw", "AnyPw1!");
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
                ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("X-Loopers-LoginId 헤더가 누락되면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdHeaderMissing() {
            // act
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginPw", "Password1!");
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response = testRestTemplate.exchange(
                ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";

        @DisplayName("정상 요청이면, 2xx 응답과 함께 저장된 비밀번호가 갱신된다.")
        @Test
        void returnsOk_whenRequestIsValid() {
            // arrange
            UserModel saved = userJpaRepository.save(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M
            ));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester01");
            headers.set("X-Loopers-LoginPw", "Password1!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass2@");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(userJpaRepository.findById(saved.getId()).orElseThrow().getPassword()).isEqualTo("NewPass2@")
            );
        }

        @DisplayName("새 비밀번호가 정책을 위반하면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordInvalid() {
            // arrange
            userJpaRepository.save(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M
            ));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester01");
            headers.set("X-Loopers-LoginPw", "Password1!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("short");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("X-Loopers-LoginPw 헤더 비밀번호가 일치하지 않으면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenAuthPasswordMismatch() {
            // arrange
            userJpaRepository.save(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M
            ));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester01");
            headers.set("X-Loopers-LoginPw", "WrongPw1!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass2@");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
