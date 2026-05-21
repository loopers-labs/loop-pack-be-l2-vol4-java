package com.loopers.interfaces.api.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
    private static final String VALID_PASSWORD = "Password1!";

    private final TestRestTemplate testRestTemplate;
    private final UserRepository userRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final PasswordEncryptor passwordEncryptor;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserRepository userRepository,
        DatabaseCleanUp databaseCleanUp,
        PasswordEncryptor passwordEncryptor
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userRepository = userRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.passwordEncryptor = passwordEncryptor;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다")
        @Test
        void returnsUserInfo_whenSignUpSucceeds() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(request), responseType);

            // then
            UserModel persisted = userRepository.findByLoginId(request.loginId()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(persisted.getId()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(request.loginId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo(request.name()),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(request.birthDate()),
                () -> assertThat(response.getBody().data().email()).isEqualTo(request.email()),
                () -> assertThat(persisted.getLoginId().value()).isEqualTo(request.loginId()),
                () -> assertThat(persisted.getName()).isEqualTo(request.name()),
                () -> assertThat(persisted.getBirthDate().value()).isEqualTo(request.birthDate()),
                () -> assertThat(persisted.getEmail().value()).isEqualTo(request.email()),
                () -> assertThat(persisted.getGender()).isEqualTo(request.gender()),
                () -> assertThat(persisted.matchesPassword(request.password(), passwordEncryptor)).isTrue()
            );
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다")
        @Test
        void returnsBadRequest_whenGenderIsNull() {
            // given
            String body = """
                {"loginId":"user01","password":"Password1!","name":"홍길동","birthDate":"1990-01-01","email":"user@example.com","gender":null}
                """;

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonStringEntity(body), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("비밀번호 RULE 위반 입력 시 400 Bad Request 를 반환한다")
        @Test
        void returnsBadRequest_whenPasswordViolatesRule() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", "pw", "홍길동", "1990-01-01", "user@example.com", Gender.MALE
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("중복된 로그인 ID 로 요청 시 409 Conflict 를 반환한다")
        @Test
        void returnsConflict_whenDuplicateLoginIdIsProvided() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(request), Void.class);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, jsonEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {
        @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다 (이름은 마스킹된 형태)")
        @Test
        void returnsMyInfo_whenUserExists() {
            // given
            String loginId = "user01";
            UserModel saved = userRepository.save(new UserModel(
                loginId, VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, authHeaderEntity(loginId, VALID_PASSWORD), responseType);

            // then
            UserModel persisted = userRepository.findByLoginId(loginId).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-01-01"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("user@example.com"),
                () -> assertThat(persisted.getId()).isEqualTo(saved.getId()),
                () -> assertThat(persisted.getName()).isEqualTo("홍길동")
            );
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없을 경우, 400 Bad Request 응답을 반환한다")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 없을 경우, 400 Bad Request 응답을 반환한다")
        @Test
        void returnsBadRequest_whenLoginPwHeaderIsMissing() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(
                loginId, VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, loginIdOnlyHeaderEntity(loginId), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("헤더 loginPw 인증이 실패할 경우, 400 Bad Request 응답을 반환한다")
        @Test
        void returnsBadRequest_whenLoginPwAuthenticationFails() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(
                loginId, VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, authHeaderEntity(loginId, "WrongPass1!"), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 ID 로 조회할 경우, 404 Not Found 응답을 반환한다")
        @Test
        void returnsNotFound_whenUserDoesNotExist() {
            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, authHeaderEntity("nonexistent", VALID_PASSWORD), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {
        @DisplayName("정상 요청 시 성공 응답을 반환한다")
        @Test
        void returnsSuccess_whenPasswordChangeSucceeds() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(
                loginId, VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PASSWORD, "NewPass99!");

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange("/api/v1/users/me/password", HttpMethod.PATCH, authJsonEntity(request, loginId, VALID_PASSWORD), responseType);

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            UserModel updated = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(updated.matchesPassword("NewPass99!", passwordEncryptor)).isTrue();
        }

        @DisplayName("기존 비밀번호 불일치 시 400 Bad Request 를 반환한다")
        @Test
        void returnsBadRequest_whenOldPasswordDoesNotMatch() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(
                loginId, VALID_PASSWORD, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
            ));
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("WrongPass1!", "NewPass99!");

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange("/api/v1/users/me/password", HttpMethod.PATCH, authJsonEntity(request, loginId, VALID_PASSWORD), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<String> jsonStringEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> authHeaderEntity(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(null, headers);
    }

    private HttpEntity<Void> loginIdOnlyHeaderEntity(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        return new HttpEntity<>(null, headers);
    }

    private <T> HttpEntity<T> authJsonEntity(T body, String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(body, headers);
    }
}
