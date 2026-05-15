package com.loopers.interfaces.api;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.domain.user.UserModel;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_PASSWORD = "/api/v1/users/me/password";
    private static final String RAW_PASSWORD = "Password1!";
    private static final String NEW_PASSWORD = "NewPassword2@";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId, String name) {
        String encoded = passwordEncoder.encode(RAW_PASSWORD);
        return userJpaRepository.save(
            new UserModel(loginId, encoded, name, LocalDate.of(1990, 1, 15), "test@example.com")
        );
    }

    private HttpHeaders authHeaders(String loginId, String rawPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", rawPassword);
        return headers;
    }

    @DisplayName("POST /api/v1/users 요청 시,")
    @Nested
    class PostRegister {

        @DisplayName("유효한 정보로 회원가입 시 200과 등록된 유저 정보가 반환된다.")
        @Test
        void returnsRegisteredUser_whenValidInfoIsProvided() {
            // arrange
            UserV1Dto.UserRegisterRequest request = new UserV1Dto.UserRegisterRequest(
                "user123", RAW_PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "hong@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserRegisterResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserRegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user123"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("hong@example.com")
            );
        }

        @DisplayName("이미 존재하는 loginId로 회원가입 시 409 CONFLICT가 반환된다.")
        @Test
        void returnsConflict_whenLoginIdIsDuplicated() {
            // arrange
            saveUser("user123", "홍길동");
            UserV1Dto.UserRegisterRequest request = new UserV1Dto.UserRegisterRequest(
                "user123", RAW_PASSWORD, "김철수", LocalDate.of(1995, 5, 20), "kim@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호 형식 위반으로 회원가입 시 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenPasswordFormatIsInvalid() {
            // arrange
            UserV1Dto.UserRegisterRequest request = new UserV1Dto.UserRegisterRequest(
                "user123", "short", "홍길동", LocalDate.of(1990, 1, 15), "hong@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me 요청 시,")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 헤더로 내 정보 조회 시 200과 마스킹된 이름이 반환된다.")
        @Test
        void returnsMaskedUserInfo_whenValidCredentialsProvided() {
            // arrange
            saveUser("user123", "홍길동");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserMeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserMeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user123", RAW_PASSWORD)), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user123"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*")
            );
        }

        @DisplayName("인증 헤더 누락으로 내 정보 조회 시 401 UNAUTHORIZED가 반환된다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("틀린 비밀번호로 내 정보 조회 시 401 UNAUTHORIZED가 반환된다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            saveUser("user123", "홍길동");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user123", "WrongPw1!")), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password 요청 시,")
    @Nested
    class PatchPassword {

        private UserModel savedUser;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            savedUser = saveUser("pwuser", "홍길동");
        }

        @DisplayName("유효한 현재·새 비밀번호로 변경 시 200이 반환되고 새 비밀번호로 인증된다.")
        @Test
        void returns200_andNewPasswordWorks_whenPasswordChangedSuccessfully() {
            // arrange
            UserV1Dto.UserChangePasswordRequest request =
                new UserV1Dto.UserChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD, HttpMethod.PATCH,
                new HttpEntity<>(request, authHeaders("pwuser", RAW_PASSWORD)), responseType);

            // assert - 변경 성공
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 새 비밀번호로 내 정보 조회 가능
            ResponseEntity<ApiResponse<UserV1Dto.UserMeResponse>> meResponse = testRestTemplate.exchange(
                ENDPOINT_ME, HttpMethod.GET,
                new HttpEntity<>(authHeaders("pwuser", NEW_PASSWORD)),
                new ParameterizedTypeReference<>() {});
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("틀린 현재 비밀번호(body)로 변경 시 401 UNAUTHORIZED가 반환된다.")
        @Test
        void returnsUnauthorized_whenCurrentPasswordInBodyIsWrong() {
            // arrange
            UserV1Dto.UserChangePasswordRequest request =
                new UserV1Dto.UserChangePasswordRequest("WrongPw1!", NEW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD, HttpMethod.PATCH,
                new HttpEntity<>(request, authHeaders("pwuser", RAW_PASSWORD)), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("현재와 동일한 새 비밀번호로 변경 시 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            UserV1Dto.UserChangePasswordRequest request =
                new UserV1Dto.UserChangePasswordRequest(RAW_PASSWORD, RAW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD, HttpMethod.PATCH,
                new HttpEntity<>(request, authHeaders("pwuser", RAW_PASSWORD)), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("형식에 맞지 않는 새 비밀번호로 변경 시 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenNewPasswordFormatIsInvalid() {
            // arrange
            UserV1Dto.UserChangePasswordRequest request =
                new UserV1Dto.UserChangePasswordRequest(RAW_PASSWORD, "short");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD, HttpMethod.PATCH,
                new HttpEntity<>(request, authHeaders("pwuser", RAW_PASSWORD)), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더 누락으로 비밀번호 변경 시 401 UNAUTHORIZED가 반환된다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // arrange
            UserV1Dto.UserChangePasswordRequest request =
                new UserV1Dto.UserChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_PASSWORD, HttpMethod.PATCH,
                new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("회원가입 후 내 정보 조회 시,")
    @Nested
    class RegisterThenGetMe {

        @DisplayName("회원가입 후 인증 헤더로 내 정보 조회 시 등록 정보와 일치하고 이름이 마스킹된다.")
        @Test
        void returnsMyInfo_afterRegistration() {
            // arrange - 회원가입
            UserV1Dto.UserRegisterRequest registerRequest = new UserV1Dto.UserRegisterRequest(
                "newuser1", RAW_PASSWORD, "김철수", LocalDate.of(1995, 5, 20), "kim@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserRegisterResponse>>() {});

            // act - 내 정보 조회
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserMeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserMeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("newuser1", RAW_PASSWORD)), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("newuser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김철*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("kim@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1995, 5, 20))
            );
        }
    }
}
