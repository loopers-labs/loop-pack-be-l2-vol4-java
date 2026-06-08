package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private final String DEFAULT_USERID   = "user1";
    private final String DEFAULT_PASSWORD = "Dlaxodid1!";
    private final String DEFAULT_NAME     = "홍길동";
    private final String DEFAULT_BIRTHDAY = "1990-01-01";
    private final String DEFAULT_EMAIL    = "test@test.com";
    private final String MASKING_NAME     = "홍길*";
    private final String NEW_PASSWORD     = "Dlaxodid2!";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveDefaultUser() {
        return userRepository.save(new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name(DEFAULT_NAME),
                new BirthDay(DEFAULT_BIRTHDAY),
                new Email(DEFAULT_EMAIL),
                UserRole.USER
        ));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", DEFAULT_USERID);
        headers.set("X-Loopers-LoginPw", DEFAULT_PASSWORD);
        return headers;
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetUser {

        @DisplayName("인증 헤더 없이 요청하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            saveDefaultUser();

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 인증 헤더로 요청하면, 200 OK와 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenAuthHeadersAreValid() {
            saveDefaultUser();

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().userId()).isEqualTo(DEFAULT_USERID);
            assertThat(response.getBody().data().name()).isEqualTo(MASKING_NAME);
            assertThat(response.getBody().data().birthDay()).isEqualTo(DEFAULT_BIRTHDAY);
            assertThat(response.getBody().data().email()).isEqualTo(DEFAULT_EMAIL);
        }
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 가입하면, 200 OK와 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenRegisterRequestIsValid() {
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                    DEFAULT_USERID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().userId()).isEqualTo(DEFAULT_USERID);
        }

        @DisplayName("필수 필드가 null이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenRequiredFieldIsNull() {
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                    null, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이미 사용 중인 아이디로 가입하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenUseridAlreadyExists() {
            saveDefaultUser();
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                    DEFAULT_USERID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("PATCH /api/v1/users/password")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 새 비밀번호로 요청하면, 200 OK와 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenNewPasswordIsValid() {
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(NEW_PASSWORD);
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users/password", HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().userId()).isEqualTo(DEFAULT_USERID);
        }

        @DisplayName("새 비밀번호가 null이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsNull() {
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(null);
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users/password", HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("현재 비밀번호와 동일한 비밀번호로 요청하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            saveDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(DEFAULT_PASSWORD);
            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange("/api/v1/users/password", HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
