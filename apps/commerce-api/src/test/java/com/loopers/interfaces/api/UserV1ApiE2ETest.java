package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Object> jsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    @DisplayName("회원가입 - POST /api/v1/users")
    @Nested
    class SignUp {

        private static Stream<UserV1Dto.SignUpRequest> missingFieldRequests() {
            String loginId = "kylekim";
            String password = "Kyle!2030";
            String name = "김카일";
            LocalDate birthDate = LocalDate.of(1995, 3, 21);
            String email = "kyle@example.com";

            return Stream.of(
                new UserV1Dto.SignUpRequest(null, password, name, birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, null, name, birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, password, null, birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, password, name, null, email),
                new UserV1Dto.SignUpRequest(loginId, password, name, birthDate, null)
            );
        }

        private static Stream<UserV1Dto.SignUpRequest> invalidFieldRequests() {
            String loginId = "kylekim";
            String password = "Kyle!2030";
            String name = "김카일";
            LocalDate birthDate = LocalDate.of(1995, 3, 21);
            String email = "kyle@example.com";

            return Stream.of(
                new UserV1Dto.SignUpRequest("abc", password, name, birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, "Ab1!", name, birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, "Ab19950321!", name, birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, password, "ABC", birthDate, email),
                new UserV1Dto.SignUpRequest(loginId, password, name, LocalDate.now().plusDays(1), email),
                new UserV1Dto.SignUpRequest(loginId, password, name, birthDate, "noatsign")
            );
        }

        @DisplayName("정상 요청이면, 201 Created와 함께 userId·loginId만 응답 본문에 담겨 반환된다.")
        @Test
        void returnsCreated_whenSignUpRequestIsValid() {
            // arrange
            UserV1Dto.SignUpRequest requestBody = new UserV1Dto.SignUpRequest(
                "kylekim",
                "Kyle!2030",
                "김카일",
                LocalDate.of(1995, 3, 21),
                "kyle@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("userId", "loginId"),
                () -> assertThat(response.getBody().data().get("loginId")).isEqualTo("kylekim"),
                () -> assertThat(response.getBody().data().get("userId")).isNotNull(),
                () -> assertThat(userJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("필수 필드가 누락되면, 400 Bad Request로 거절되고 회원은 생성되지 않는다.")
        @ParameterizedTest
        @MethodSource("missingFieldRequests")
        void returnsBadRequest_whenRequiredFieldIsMissing(UserV1Dto.SignUpRequest requestBody) {
            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().data()).isNull(),
                () -> assertThat(userJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("필드 정책을 위반한 입력이면, 400 Bad Request로 거절되고 회원은 생성되지 않는다.")
        @ParameterizedTest
        @MethodSource("invalidFieldRequests")
        void returnsBadRequest_whenFieldPolicyIsViolated(UserV1Dto.SignUpRequest requestBody) {
            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().data()).isNull(),
                () -> assertThat(userJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("password가 정책을 위반하면, 응답 메시지에 평문 password가 노출되지 않는다.")
        @Test
        void doesNotLeakRawPassword_whenPasswordPolicyIsViolated() {
            // arrange
            String rawPassword = "Ab1!";
            UserV1Dto.SignUpRequest requestBody = new UserV1Dto.SignUpRequest(
                "kylekim",
                rawPassword,
                "김카일",
                LocalDate.of(1995, 3, 21),
                "kyle@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonRequest(requestBody),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().message()).doesNotContain(rawPassword)
            );
        }

        @DisplayName("이미 사용 중인 loginId로 재가입을 시도하면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenLoginIdIsAlreadyUsed() {
            // arrange
            String loginId = "kylekim";
            UserV1Dto.SignUpRequest firstRequest = new UserV1Dto.SignUpRequest(
                loginId,
                "Kyle!2030",
                "김카일",
                LocalDate.of(1995, 3, 21),
                "kyle@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonRequest(firstRequest), Void.class);

            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                loginId,
                "Park!2030",
                "박루퍼",
                LocalDate.of(1998, 7, 12),
                "park@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonRequest(duplicateRequest),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(userJpaRepository.findAll()).hasSize(1)
            );
        }

        @DisplayName("이미 사용 중인 email로 재가입을 시도하면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenEmailIsAlreadyUsed() {
            // arrange
            String email = "kyle@example.com";
            UserV1Dto.SignUpRequest firstRequest = new UserV1Dto.SignUpRequest(
                "kylekim",
                "Kyle!2030",
                "김카일",
                LocalDate.of(1995, 3, 21),
                email
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonRequest(firstRequest), Void.class);

            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                "parkruper",
                "Park!2030",
                "박루퍼",
                LocalDate.of(1998, 7, 12),
                email
            );

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                jsonRequest(duplicateRequest),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(userJpaRepository.findAll()).hasSize(1)
            );
        }
    }
}
