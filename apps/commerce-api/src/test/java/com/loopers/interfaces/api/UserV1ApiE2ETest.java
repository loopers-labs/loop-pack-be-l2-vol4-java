package com.loopers.interfaces.api;

import com.loopers.domain.user.LoginId;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
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

    private static UserV1Dto.SignUpRequest validRequest() {
        return new UserV1Dto.SignUpRequest(
            "kim99",
            "Abcd123!",
            "홍길동",
            LocalDate.of(1999, 1, 1),
            "kim@loopers.com"
        );
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> postSignUp(UserV1Dto.SignUpRequest request) {
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> getMe(String loginId, String password) {
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        HttpHeaders headers = new HttpHeaders();
        if (loginId != null) {
            headers.add("X-Loopers-LoginId", loginId);
        }
        if (password != null) {
            headers.add("X-Loopers-LoginPw", password);
        }
        return testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("정상 요청이면 201 CREATED 와 마스킹된 이름의 응답을 반환하고, 비밀번호는 해시로 저장된다.")
        @Test
        void returnsCreatedAndStoresEncodedPassword_whenRequestIsValid() {
            // arrange
            UserV1Dto.SignUpRequest request = validRequest();

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("kim99"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1999, 1, 1)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("kim@loopers.com")
            );
            UserModel saved = userJpaRepository.findAll().get(0);
            assertThat(saved.getEncodedPassword()).isNotEqualTo("Abcd123!");
            assertThat(saved.getEncodedPassword()).startsWith("$2a$");
        }

        @DisplayName("이미 존재하는 loginId 로 요청하면 409 CONFLICT 를 반환한다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            postSignUp(validRequest());

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(validRequest());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("loginId 가 영문/숫자 외 문자를 포함하면 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdContainsInvalidCharacters() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "kim_99", "Abcd123!", "홍길동", LocalDate.of(1999, 1, 1), "kim@loopers.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("email 포맷이 잘못되면 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenEmailIsInvalid() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "kim99", "Abcd123!", "홍길동", LocalDate.of(1999, 1, 1), "not-an-email"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("birthDate 가 미래이면 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenBirthDateIsFuture() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "kim99", "Abcd123!", "홍길동", LocalDate.now().plusDays(1), "kim@loopers.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 길이/문자 정책을 위반하면 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordViolatesPolicy() {
            // arrange
            UserV1Dto.SignUpRequest tooShort = new UserV1Dto.SignUpRequest(
                "kim99", "Aa1!aa1", "홍길동", LocalDate.of(1999, 1, 1), "kim@loopers.com"
            );
            UserV1Dto.SignUpRequest hasKorean = new UserV1Dto.SignUpRequest(
                "kim99", "Abcd123!한", "홍길동", LocalDate.of(1999, 1, 1), "kim@loopers.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> tooShortResponse = postSignUp(tooShort);
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> hasKoreanResponse = postSignUp(hasKorean);

            // assert
            assertAll(
                () -> assertThat(tooShortResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(hasKoreanResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("필수 필드(loginId) 가 누락되면 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenRequiredFieldIsMissing() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                null, "Abcd123!", "홍길동", LocalDate.of(1999, 1, 1), "kim@loopers.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일 substring 이 포함되면 400 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "kim99", "Aa1!19990101", "홍길동", LocalDate.of(1999, 1, 1), "kim@loopers.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.existsByLoginId(new LoginId("kim99"))).isFalse()
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("정상 자격이면 200 과 마스킹된 이름을 반환한다.")
        @Test
        void returnsOkWithMaskedName_whenCredentialsAreValid() {
            // arrange
            postSignUp(validRequest());

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = getMe("kim99", "Abcd123!");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("kim99"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1999, 1, 1)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("kim@loopers.com")
            );
        }

        @DisplayName("X-Loopers-LoginId 헤더가 누락되면 401 UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenLoginIdHeaderIsMissing() {
            // arrange
            postSignUp(validRequest());

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = getMe(null, "Abcd123!");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 누락되면 401 UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenPasswordHeaderIsMissing() {
            // arrange
            postSignUp(validRequest());

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = getMe("kim99", null);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 loginId 면 401 UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenLoginIdDoesNotExist() {
            // arrange
            postSignUp(validRequest());

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = getMe("nobody", "Abcd123!");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면 401 UNAUTHORIZED 를 반환한다.")
        @Test
        void returnsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            postSignUp(validRequest());

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = getMe("kim99", "WrongPass1!");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
