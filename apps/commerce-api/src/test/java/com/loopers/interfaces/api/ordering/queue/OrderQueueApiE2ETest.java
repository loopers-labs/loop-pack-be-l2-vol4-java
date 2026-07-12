package com.loopers.interfaces.api.ordering.queue;

import com.loopers.application.ordering.queue.OrderQueueAdmissionWorker;
import com.loopers.domain.ordering.queue.OrderQueueStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "commerce.workers.order-queue.enabled=false"
)
class OrderQueueApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final OrderQueueAdmissionWorker orderQueueAdmissionWorker;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    OrderQueueApiE2ETest(
        TestRestTemplate testRestTemplate,
        OrderQueueAdmissionWorker orderQueueAdmissionWorker,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.orderQueueAdmissionWorker = orderQueueAdmissionWorker;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("유저 헤더로 대기열에 진입하면 WAITING 상태와 순번을 반환한다.")
        @Test
        void entersOrderQueue() {
            // arrange

            // act
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> response = enter("user1");

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderQueueStatus.WAITING),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().waitingCount()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().recommendedPollingIntervalSeconds()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("같은 사용자가 다시 진입해도 기존 순번을 유지한다.")
        @Test
        void keepsOriginalPosition_whenSameUserEntersAgain() {
            // arrange
            enter("user1");
            enter("user2");

            // act
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> response = enter("user1");

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderQueueStatus.WAITING),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().waitingCount()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().recommendedPollingIntervalSeconds()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("유저 헤더가 없으면 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenUserHeaderIsMissing() {
            // arrange

            // act
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> response = exchange(
                "/api/v1/queue/enter",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class GetPosition {

        @DisplayName("대기열에 있는 사용자의 현재 순번을 반환한다.")
        @Test
        void returnsWaitingPosition() {
            // arrange
            enter("user1");

            // act
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> response = getPosition("user1");

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderQueueStatus.WAITING),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().waitingCount()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().recommendedPollingIntervalSeconds()).isEqualTo(1L)
            );
        }

        @DisplayName("대기열과 입장 토큰이 없으면 NOT_QUEUED를 반환한다.")
        @Test
        void returnsNotQueued_whenUserHasNoQueueState() {
            // arrange

            // act
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> response = getPosition("user1");

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderQueueStatus.NOT_QUEUED),
                () -> assertThat(response.getBody().data().position()).isNull(),
                () -> assertThat(response.getBody().data().waitingCount()).isZero(),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isNull(),
                () -> assertThat(response.getBody().data().recommendedPollingIntervalSeconds()).isNull(),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("입장 처리된 사용자는 READY 상태와 입장 토큰을 조회한다.")
        @Test
        void returnsReadyWithToken_whenUserIsAdmitted() {
            // arrange
            enter("user1");
            orderQueueAdmissionWorker.admitNext();

            // act
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> response = getPosition("user1");

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderQueueStatus.READY),
                () -> assertThat(response.getBody().data().position()).isNull(),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isZero(),
                () -> assertThat(response.getBody().data().recommendedPollingIntervalSeconds()).isZero(),
                () -> assertThat(response.getBody().data().token()).isNotBlank()
            );
        }

        @DisplayName("대기 인원이 배치 크기를 초과하면 일부만 READY가 되고 나머지는 WAITING으로 남는다.")
        @Test
        void keepsOverflowUsersWaiting_whenQueueExceedsAdmissionBatchSize() {
            // arrange
            for (int i = 1; i <= 12; i++) {
                enter("user" + i);
            }

            // act
            orderQueueAdmissionWorker.admitNext();
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> admitted = getPosition("user10");
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> overflow = getPosition("user11");
            ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> last = getPosition("user12");

            // assert
            assertAll(
                () -> assertTrue(admitted.getStatusCode().is2xxSuccessful()),
                () -> assertThat(admitted.getBody().data().status()).isEqualTo(OrderQueueStatus.READY),
                () -> assertThat(admitted.getBody().data().token()).isNotBlank(),
                () -> assertTrue(overflow.getStatusCode().is2xxSuccessful()),
                () -> assertThat(overflow.getBody().data().status()).isEqualTo(OrderQueueStatus.WAITING),
                () -> assertThat(overflow.getBody().data().position()).isEqualTo(1L),
                () -> assertThat(overflow.getBody().data().waitingCount()).isEqualTo(2L),
                () -> assertThat(overflow.getBody().data().recommendedPollingIntervalSeconds()).isEqualTo(1L),
                () -> assertThat(overflow.getBody().data().token()).isNull(),
                () -> assertTrue(last.getStatusCode().is2xxSuccessful()),
                () -> assertThat(last.getBody().data().status()).isEqualTo(OrderQueueStatus.WAITING),
                () -> assertThat(last.getBody().data().position()).isEqualTo(2L),
                () -> assertThat(last.getBody().data().waitingCount()).isEqualTo(2L),
                () -> assertThat(last.getBody().data().recommendedPollingIntervalSeconds()).isEqualTo(1L)
            );
        }
    }

    private ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> enter(String userId) {
        return exchange(
            "/api/v1/queue/enter",
            HttpMethod.POST,
            new HttpEntity<>(userHeaders(userId)),
            new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<OrderQueueDto.OrderQueueResponse>> getPosition(String userId) {
        return exchange(
            "/api/v1/queue/position",
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(userId)),
            new ParameterizedTypeReference<>() {}
        );
    }

    private <T> ResponseEntity<T> exchange(
        String url,
        HttpMethod method,
        HttpEntity<?> request,
        ParameterizedTypeReference<T> responseType
    ) {
        return testRestTemplate.exchange(url, method, request, responseType);
    }

    private HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.LOGIN_ID, userId);
        headers.add(HeaderValidator.LOGIN_PW, "password");
        return headers;
    }
}
