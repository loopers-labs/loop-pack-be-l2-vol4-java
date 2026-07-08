package com.loopers.interfaces.api;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String ENTER = "/api/v1/queue/enter";
    private static final String POSITION = "/api/v1/queue/position";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Abcd1234!";

    private final TestRestTemplate testRestTemplate;
    private final UserFacade userFacade;
    private final EntryTokenRepository entryTokenRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long userId;

    @Autowired
    public QueueV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserFacade userFacade,
        EntryTokenRepository entryTokenRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userFacade = userFacade;
        this.entryTokenRepository = entryTokenRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        userId = userFacade.signUp(new UserCommand.SignUp(
            LOGIN_ID,
            LOGIN_PW,
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        return headers;
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("대기열에 진입하면 200 과 순번 0, 전체 대기 인원 1을 반환한다.")
        @Test
        void entersQueueAndReturnsPositionZero() {
            // when
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                ENTER, HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().position()).isZero(),
                () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class Position {

        @DisplayName("대기 중인 유저가 조회하면 200 과 순번·예상 대기를 반환하고 토큰은 없다.")
        @Test
        void returnsPositionAndEstimatedWaitWhileWaiting() {
            // given
            testRestTemplate.exchange(ENTER, HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {});

            // when
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                POSITION, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().position()).isZero(),
                () -> assertThat(response.getBody().data().estimatedWait()).isEqualTo("곧 주문 가능"),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("입장된(토큰 보유) 유저가 조회하면 200 과 함께 응답에 토큰이 포함된다.")
        @Test
        void includesTokenWhenAdmitted() {
            // given - 스케줄러가 입장시켜 토큰을 발급한 상태 재현
            EntryToken issued = entryTokenRepository.issue(userId, Duration.ofMinutes(5));

            // when
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                POSITION, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().token()).isEqualTo(issued.value()),
                () -> assertThat(response.getBody().data().estimatedWait()).isEqualTo("곧 주문 가능")
            );
        }

        @DisplayName("대기열에도 없고 토큰도 없는 유저가 조회하면 NOT_FOUND 와 QUEUE_ENTRY_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFoundWhenNeverEnrolled() {
            // when
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                POSITION, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("QUEUE_ENTRY_NOT_FOUND")
            );
        }
    }
}
