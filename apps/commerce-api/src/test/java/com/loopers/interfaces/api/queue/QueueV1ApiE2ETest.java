package com.loopers.interfaces.api.queue;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

// taskScheduler를 @MockitoBean으로 무력화해 실제 QueueAdmissionScheduler(100ms 주기)가 이 테스트가
// 대기열에 넣어둔 유저를 assertion 전에 먼저 발급/제거하지 않게 한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String LOGIN_PW = "Password1!";

    @MockitoBean(name = "taskScheduler")
    private TaskScheduler taskScheduler;

    private final TestRestTemplate testRestTemplate;
    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public QueueV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserRepository userRepository,
            PasswordEncryptor passwordEncryptor,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userRepository = userRepository;
        this.passwordEncryptor = passwordEncryptor;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void saveUser(String loginId) {
        userRepository.save(new UserModel(
                loginId, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
        ));
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("대기열 진입에 성공하면 position 필드를 응답으로 반환한다.")
        @Test
        void returnsPosition_whenEnterSucceeds() {
            // given
            saveUser("user01");

            // when
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response =
                    testRestTemplate.exchange("/api/v1/queue/enter", HttpMethod.POST, authHeaderEntity("user01", LOGIN_PW), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(0L)
            );
        }

        @DisplayName("인증 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeadersAreMissing() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange("/api/v1/queue/enter", HttpMethod.POST, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class GetPosition {

        @DisplayName("대기 순번 조회에 성공하면 position/totalWaiting/estimatedWaitSeconds/token 필드를 응답으로 반환한다.")
        @Test
        void returnsPositionFields_whenGetPositionSucceeds() {
            // given
            saveUser("user01");
            testRestTemplate.exchange(
                    "/api/v1/queue/enter", HttpMethod.POST, authHeaderEntity("user01", LOGIN_PW), Void.class
            );

            // when
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response =
                    testRestTemplate.exchange("/api/v1/queue/position", HttpMethod.GET, authHeaderEntity("user01", LOGIN_PW), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(0L),
                    () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(1L),
                    () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isEqualTo(0L),
                    () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("인증 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeadersAreMissing() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange("/api/v1/queue/position", HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private HttpEntity<Void> authHeaderEntity(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(null, headers);
    }
}
