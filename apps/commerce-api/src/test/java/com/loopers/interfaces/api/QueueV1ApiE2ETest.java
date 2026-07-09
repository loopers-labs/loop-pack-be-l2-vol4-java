package com.loopers.interfaces.api;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String ENTER = "/api/v1/queue/enter";
    private static final String POSITION = "/api/v1/queue/position";
    private static final String PASSWORD = "Abcd123!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntryTokenRepository entryTokenRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long user1Id;

    @Autowired
    public QueueV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserJpaRepository userJpaRepository,
            PasswordEncoder passwordEncoder,
            EntryTokenRepository entryTokenRepository,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.entryTokenRepository = entryTokenRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        user1Id = createUser("user1");
        createUser("user2");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createUser(String loginId) {
        UserModel user = new UserModel(
                new LoginId(loginId),
                new Name("유저"),
                new BirthDate(LocalDate.of(1999, 1, 1)),
                new Email(loginId + "@loopers.com"),
                EncodedPassword.create(passwordEncoder, PASSWORD)
        );
        return userJpaRepository.save(user).getId();
    }

    private static HttpHeaders loginHeader(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Loopers-LoginId", loginId);
        headers.add("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> enter(String loginId) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENTER, HttpMethod.POST, new HttpEntity<>(loginHeader(loginId)), type);
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> getPosition(String loginId) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(POSITION, HttpMethod.GET, new HttpEntity<>(loginHeader(loginId)), type);
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("진입 순서대로 1-based 순번과 예상 대기 시간·폴링 간격을 반환한다.")
        @Test
        void returnsPositionInEntryOrder() {
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> first = enter("user1");
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> second = enter("user2");

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            QueueV1Dto.PositionResponse firstBody = first.getBody().data();
            assertThat(firstBody.position()).isEqualTo(1);
            assertThat(firstBody.estimatedWaitSeconds()).isGreaterThanOrEqualTo(1);
            assertThat(firstBody.pollAfterMillis()).isEqualTo(1000);
            assertThat(firstBody.token()).isNull();

            assertThat(second.getBody().data().position()).isEqualTo(2);
        }

        @DisplayName("재진입해도 순번이 뒤로 밀리지 않는다(멱등).")
        @Test
        void reentryKeepsPosition() {
            enter("user1");
            enter("user2");

            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> reentry = enter("user1");

            assertThat(reentry.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(reentry.getBody().data().position()).isEqualTo(1);
        }

        @DisplayName("이미 토큰을 받은 유저는 다시 줄 세우지 않고 토큰을 반환한다.")
        @Test
        void returnsTokenInsteadOfReQueueing_whenAlreadyIssued() {
            entryTokenRepository.issue(user1Id, "issued-token", Duration.ofMinutes(5));

            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = enter("user1");

            QueueV1Dto.PositionResponse body = response.getBody().data();
            assertThat(body.position()).isZero();
            assertThat(body.token()).isEqualTo("issued-token");
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class Position {

        @DisplayName("대기 중이면 현재 순번과 예상 대기 시간을 반환한다.")
        @Test
        void returnsCurrentPosition_whileWaiting() {
            enter("user1");
            enter("user2");

            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = getPosition("user2");

            QueueV1Dto.PositionResponse body = response.getBody().data();
            assertThat(body.position()).isEqualTo(2);
            assertThat(body.token()).isNull();
            assertThat(body.totalWaiting()).isEqualTo(2); // 전체 대기 인원 = 진입한 2명
        }

        @DisplayName("토큰이 발급된 유저에게는 position=0 과 토큰을 반환한다.")
        @Test
        void returnsToken_whenIssued() {
            entryTokenRepository.issue(user1Id, "issued-token", Duration.ofMinutes(5));

            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = getPosition("user1");

            QueueV1Dto.PositionResponse body = response.getBody().data();
            assertThat(body.position()).isZero();
            assertThat(body.estimatedWaitSeconds()).isZero();
            assertThat(body.pollAfterMillis()).isNull();
            assertThat(body.token()).isEqualTo("issued-token");
        }

        @DisplayName("대기열에 없는 유저는 404 를 반환한다 — 재진입을 안내한다.")
        @Test
        void returnsNotFound_whenNotInQueue() {
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = getPosition("user1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
