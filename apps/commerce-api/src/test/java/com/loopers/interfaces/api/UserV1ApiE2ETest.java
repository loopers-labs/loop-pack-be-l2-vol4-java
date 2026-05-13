package com.loopers.interfaces.api;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// TODO: webMvcTest와의 차이점 분석 및 공부
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";

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
    class Signup {

        @DisplayName("정상 정보로 회원가입 시, 가입된 사용자 정보를 반환한다.")
        @Test
        void returnsUser_whenSignupRequestIsValid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "loopers123",
                "Pass1234!",
                "김민우",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers123"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김민우"),
                () -> assertThat(response.getBody().data().birth()).isEqualTo(LocalDate.of(1990, 1, 1)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("user@example.com")
            );
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입 시, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignupRequest first = new UserV1Dto.SignupRequest(
                "loopers123",
                "Pass1234!",
                "김민우",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                new HttpEntity<>(first), responseType);

            UserV1Dto.SignupRequest duplicated = new UserV1Dto.SignupRequest(
                "loopers123",
                "Pass5678!",
                "이지은",
                LocalDate.of(1995, 5, 5),
                "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(duplicated), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("형식이 잘못된 정보로 회원가입 시, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenRequestIsInvalid() {
            // arrange — 잘못된 이메일 형식 (대표 invalid 케이스)
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "loopers123",
                "Pass1234!",
                "김민우",
                LocalDate.of(1990, 1, 1),
                "not-an-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(userJpaRepository.findByLoginId("loopers123")).isEmpty();
        }

        @DisplayName("이미 등록된 email 로 회원가입 시, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenEmailAlreadyExists() {
            // arrange
            UserV1Dto.SignupRequest first = new UserV1Dto.SignupRequest(
                "loopers123",
                "Pass1234!",
                "김민우",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                new HttpEntity<>(first), responseType);

            UserV1Dto.SignupRequest duplicated = new UserV1Dto.SignupRequest(
                "loopers456",
                "Pass5678!",
                "이지은",
                LocalDate.of(1995, 5, 5),
                "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(duplicated), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("대소문자만 다른 email 로 회원가입 시, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenEmailDiffersOnlyInCase() {
            // arrange
            UserV1Dto.SignupRequest first = new UserV1Dto.SignupRequest(
                "loopers123",
                "Pass1234!",
                "김민우",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                new HttpEntity<>(first), responseType);

            UserV1Dto.SignupRequest duplicated = new UserV1Dto.SignupRequest(
                "loopers456",
                "Pass5678!",
                "이지은",
                LocalDate.of(1995, 5, 5),
                "User@Example.COM"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(duplicated), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class FindMyInfo {

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, 401 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderIsMissing() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginPw", "Passw0rd!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 헤더로 요청하면, 200 과 마지막 글자가 마스킹된 사용자 정보를 반환한다.")
        @Test
        void returnsMyInfo_whenHeadersAreValid() {
            // arrange — 회원가입
            UserV1Dto.SignupRequest signup = new UserV1Dto.SignupRequest(
                "minwoo01",
                "Passw0rd!",
                "김민우",
                LocalDate.of(1990, 1, 1),
                "minwoo@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> signupResponseType =
                new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                new HttpEntity<>(signup), signupResponseType);

            // arrange — 헤더 셋업
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "minwoo01");
            headers.set("X-Loopers-LoginPw", "Passw0rd!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("minwoo01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김민*"),
                () -> assertThat(response.getBody().data().birth()).isEqualTo(LocalDate.of(1990, 1, 1)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("minwoo@example.com")
            );
        }
    }
}
