package com.loopers.interfaces.api;

import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String SIGN_UP_PATH = "/api/v1/users";
    private static final String ME_PATH = "/api/v1/users/me";
    private static final String CHANGE_PW_PATH = "/api/v1/users/me/password";

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

    // 헬퍼: 헤더 빌더
    private HttpHeaders authHeaders(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        return headers;
    }

    // 헬퍼: 회원가입 1명
    private void signUp(String loginId, String password, String name) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                loginId, password, name,
                LocalDate.of(1992, 6, 24), "test@example.com"
        );
        testRestTemplate.postForEntity(SIGN_UP_PATH, request, Object.class);
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("정상 정보로 가입하면, 200과 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMaskedUserInfo_whenSignUpSucceeds() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testid", "testPw1234", "테스터",
                    LocalDate.of(1992, 6, 24), "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(SIGN_UP_PATH, HttpMethod.POST,
                            new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("testid"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스*"),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        @DisplayName("올바른 헤더로 조회하면, 200과 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMaskedUserInfo_whenAuthenticated() {
            // arrange
            signUp("testid", "testPw1234", "테스터");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ME_PATH, HttpMethod.GET,
                            new HttpEntity<>(authHeaders("testid", "testPw1234")), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스*")
            );
        }

        @DisplayName("잘못된 비밀번호 헤더로 조회하면, 401을 반환한다.")
        @Test
        void returns401_whenWrongPassword() {
            // arrange
            signUp("testid", "testPw1234", "테스터");

            // act
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    ME_PATH, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("testid", "wrongPw9999")), Object.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 헤더와 새 비밀번호로 요청하면, 200을 반환한다.")
        @Test
        void returns200_whenChangePasswordSucceeds() {
            // arrange
            signUp("testid", "testPw1234", "테스터");
            UserV1Dto.ChangePasswordRequest request =
                    new UserV1Dto.ChangePasswordRequest("newPw5678");

            // act
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    CHANGE_PW_PATH, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("testid", "testPw1234")),
                    Object.class);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

            // act 2: 새 비번으로 GET /me 가능한지 검증
            ResponseEntity<Object> getMeAfter = testRestTemplate.exchange(
                    ME_PATH, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("testid", "newPw5678")), Object.class);
            assertThat(getMeAfter.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @DisplayName("잘못된 비밀번호 헤더로 요청하면, 401을 반환한다.")
        @Test
        void returns401_whenWrongCurrentPassword() {
            // arrange
            signUp("testid", "testPw1234", "테스터");
            UserV1Dto.ChangePasswordRequest request =
                    new UserV1Dto.ChangePasswordRequest("newPw5678");

            // act
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    CHANGE_PW_PATH, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("testid", "wrongPw9999")),
                    Object.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
