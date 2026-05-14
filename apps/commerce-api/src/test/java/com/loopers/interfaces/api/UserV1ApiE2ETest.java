package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/users";

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

        @DisplayName("유효한 모든 필드로 가입 시, 사용자 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenAllFieldsAreValid() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김철수"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1999-03-22"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("user@example.com"),
                () -> {
                    UserModel saved = userJpaRepository.findById(response.getBody().data().id()).orElseThrow();
                    assertThat(new BCryptPasswordEncoder().matches("Abcd1234!", saved.getPassword().getValue())).isTrue();
                }
            );
        }

        @DisplayName("이미 가입된 로그인 ID 로 가입을 시도하면, CONFLICT 를 반환한다.")
        @Test
        void returnsConflict_whenLoginIdIsAlreadyTaken() {
            // given - 동일 loginId 사전 가입
            UserV1Dto.SignUpRequest firstRequest = new UserV1Dto.SignUpRequest(
                "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(firstRequest), responseType);

            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                "user01", "Xyz!9876@", "이영희", LocalDate.of(2000, 5, 10), "duplicate@example.com"
            );

            // when
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().message()).isEqualTo("이미 사용 중인 로그인 ID 입니다.")
            );
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDate() {
            // given - 1999-03-22 를 비밀번호에 yyyyMMdd 형식으로 포함
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", "ab19990322!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().message()).isEqualTo("비밀번호에 생년월일을 포함할 수 없습니다.")
            );
        }

        @DisplayName("비밀번호 형식이 유효하지 않으면, BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordFormatIsInvalid() {
            // given - 7자 비밀번호 (최소 8자 미달)
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", "Abc123!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().message()).isEqualTo("비밀번호는 영문 대/소문자, 숫자, 특수문자로 8~16자여야 합니다.")
            );
        }
    }

}
