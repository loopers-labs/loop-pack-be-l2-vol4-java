package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserV1ApiE2ETest {
    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_USER_ID = "usertest123";
    private static final String DEFAULT_PASSWORD = "abc123!@#";
    private static final String DEFAULT_NAME = "홍길동";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1995, 6, 10);
    private static final String DEFAULT_EMAIL = "test@naver.com";

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

    // 회원가입
    @DisplayName("POST /api/v1/users")
    @Nested
    class Signup {
        String requestUrl = "/api/v1/users";

        @DisplayName("유효한 회원가입 정보를 주면, 회원가입이 성공한다.")
        @Test
        void successSignup_whenValidSignupInfoIsProvided() {
            // arrange
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<ApiResponse<Void>>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(requestUrl, HttpMethod.POST, new HttpEntity<>(signupRequest), responseType);

            // assert
            Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("이미 존재하는 회원 ID를 주면, 회원가입이 실패한다.")
        @Test
        void failSignup_whenExistingUserIdIsProvided() {
            // arrange
            // 기존 user 저장
            UserModel userModel = new UserModel(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);
            userJpaRepository.save(userModel);

            // 기존 userId와 동일한 신규 user 생성 요청
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<ApiResponse<Void>>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(requestUrl, HttpMethod.POST, new HttpEntity<>(signupRequest), responseType);

            // assert
            Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // 내 정보 조회
    @DisplayName("GET /api/v1/users/myInfo")
    @Nested
    class GetUser {

    }

    // 비밀번호 변경
    @DisplayName("PATCH /api/v1/users/myInfo/changePassword")
    @Nested
    class ChangePassword {

    }
}
