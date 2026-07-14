package com.loopers.interfaces.api;

import com.loopers.interfaces.api.queue.QueueV1Dto;
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
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 대기열 API E2E — 게이트 off 면 대기열 API 가 400 거부되므로(대기열 미운영) 게이트 on 으로 띄우되,
 * 순번 단언의 결정성을 위해 스케줄러 주기를 10분(600000ms)으로 늘린다:
 * 스케줄러 빈은 뜨지만 기동 직후 1회 tick 은 빈 큐 no-op 이고, 이후 테스트 동안 다시 돌지 않는다.
 *
 * DirtiesContext(AFTER_CLASS): 이 컨텍스트가 캐시에 살아남으면 10분 뒤 tick 이 같은 Redis 를 쓰는
 * 다른 대기열 테스트의 대기 유저를 입장시킬 수 있다 — 클래스 종료 시 컨텍스트를 닫아 오염을 차단한다.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "queue.order-gate.enabled=true",
        "queue.admission.interval-ms=600000"
    }
)
class QueueV1ApiE2ETest {

    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";

    private final TestRestTemplate testRestTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public QueueV1ApiE2ETest(TestRestTemplate testRestTemplate, RedisCleanUp redisCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 자기완결 클린업 — "기동 직후 1회 tick 은 빈 큐 no-op" 같은 스위트 순서 의존에 기대지 않는다
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, loginId);
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> enter(String loginId) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENTER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(authHeaders(loginId)), responseType
        );
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> position(String loginId) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            POSITION_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders(loginId)), responseType
        );
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("첫 진입이면, 2xx 응답과 함께 순번 1·대기 인원 1·예상 대기·권장 폴링 간격을 반환한다.")
        @Test
        void returnsFirstPosition_whenFirstEnter() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = enter("tester01");

            // assert — 예상 대기 시간의 정확한 산술은 단위 테스트가 검증(여기선 테스트용 주기 10분이 반영된 양수)
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1),
                () -> assertThat(response.getBody().data().waitingCount()).isEqualTo(1),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isPositive(),
                () -> assertThat(response.getBody().data().suggestedPollIntervalSeconds()).isEqualTo(1),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("재진입해도 줄 뒤로 밀리지 않고 기존 순번을 반환한다. (멱등)")
        @Test
        void keepsPosition_whenReenter() {
            // arrange
            enter("tester01");
            enter("tester02");

            // act — 뒤에 다른 유저가 있는 상태에서 재진입
            ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = enter("tester01");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1),
                () -> assertThat(response.getBody().data().waitingCount()).isEqualTo(2)
            );
        }

        @DisplayName("인증 헤더가 없으면 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = testRestTemplate.exchange(
                ENTER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class Position {

        @DisplayName("대기 중이면, 순번(진입 순서)·전체 대기 인원·예상 대기 시간을 반환한다.")
        @Test
        void returnsPosition_whenWaiting() {
            // arrange
            enter("tester01");
            enter("tester02");

            // act
            ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = position("tester02");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().position()).isEqualTo(2),
                () -> assertThat(response.getBody().data().waitingCount()).isEqualTo(2),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isPositive(),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("대기열에 진입한 적 없으면 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenNeverEntered() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = position("ghost");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
