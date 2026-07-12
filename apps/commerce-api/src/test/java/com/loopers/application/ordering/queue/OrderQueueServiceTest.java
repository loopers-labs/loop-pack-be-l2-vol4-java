package com.loopers.application.ordering.queue;

import com.loopers.domain.ordering.queue.OrderQueueRepository;
import com.loopers.domain.ordering.queue.OrderQueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderQueueServiceTest {

    private final FakeOrderQueueRepository orderQueueRepository = new FakeOrderQueueRepository();
    private final OrderQueueService orderQueueService = new OrderQueueService(
        orderQueueRepository,
        Duration.ofMinutes(5),
        10,
        1000L
    );

    @DisplayName("토큰이 있으면 대기열에 다시 넣지 않고 READY를 우선 반환한다.")
    @Test
    void returnsReady_whenTokenAlreadyExists() {
        // arrange
        String token = orderQueueRepository.issueToken("user1", Duration.ofMinutes(5));

        // act
        OrderQueueResult result = orderQueueService.enter("user1");

        // assert
        assertAll(
            () -> assertThat(result.status()).isEqualTo(OrderQueueStatus.READY),
            () -> assertThat(result.position()).isNull(),
            () -> assertThat(result.waitingCount()).isZero(),
            () -> assertThat(result.estimatedWaitSeconds()).isZero(),
            () -> assertThat(result.recommendedPollingIntervalSeconds()).isZero(),
            () -> assertThat(result.token()).isEqualTo(token)
        );
    }

    @DisplayName("토큰이 없으면 대기열에 진입시키고 WAITING과 예상 대기 시간을 반환한다.")
    @Test
    void entersQueue_whenTokenDoesNotExist() {
        // arrange
        for (int i = 1; i <= 10; i++) {
            orderQueueRepository.enter("existing" + i);
        }

        // act
        OrderQueueResult result = orderQueueService.enter("user1");

        // assert
        assertAll(
            () -> assertThat(result.status()).isEqualTo(OrderQueueStatus.WAITING),
            () -> assertThat(result.position()).isEqualTo(11L),
            () -> assertThat(result.waitingCount()).isEqualTo(11L),
            () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(2L),
            () -> assertThat(result.recommendedPollingIntervalSeconds()).isEqualTo(2L),
            () -> assertThat(result.token()).isNull()
        );
    }

    @DisplayName("대기 순번이 뒤로 밀리면 권장 polling 간격을 늘리되 최대 10초로 제한한다.")
    @Test
    void recommendsPollingIntervalByWaitingPosition() {
        // arrange
        for (int i = 1; i <= 100; i++) {
            orderQueueRepository.enter("existing" + i);
        }

        // act
        OrderQueueResult result = orderQueueService.enter("user1");

        // assert
        assertAll(
            () -> assertThat(result.status()).isEqualTo(OrderQueueStatus.WAITING),
            () -> assertThat(result.position()).isEqualTo(101L),
            () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(11L),
            () -> assertThat(result.recommendedPollingIntervalSeconds()).isEqualTo(10L)
        );
    }

    @DisplayName("순번 조회는 READY, WAITING, NOT_QUEUED를 구분한다.")
    @Test
    void returnsCurrentPositionByQueueState() {
        // arrange
        String readyToken = orderQueueRepository.issueToken("readyUser", Duration.ofMinutes(5));
        orderQueueRepository.enter("waitingUser");

        // act
        OrderQueueResult ready = orderQueueService.getPosition("readyUser");
        OrderQueueResult waiting = orderQueueService.getPosition("waitingUser");
        OrderQueueResult notQueued = orderQueueService.getPosition("notQueuedUser");

        // assert
        assertAll(
            () -> assertThat(ready.status()).isEqualTo(OrderQueueStatus.READY),
            () -> assertThat(ready.estimatedWaitSeconds()).isZero(),
            () -> assertThat(ready.recommendedPollingIntervalSeconds()).isZero(),
            () -> assertThat(ready.token()).isEqualTo(readyToken),
            () -> assertThat(waiting.status()).isEqualTo(OrderQueueStatus.WAITING),
            () -> assertThat(waiting.position()).isEqualTo(1L),
            () -> assertThat(waiting.estimatedWaitSeconds()).isEqualTo(1L),
            () -> assertThat(waiting.recommendedPollingIntervalSeconds()).isEqualTo(1L),
            () -> assertThat(notQueued.status()).isEqualTo(OrderQueueStatus.NOT_QUEUED),
            () -> assertThat(notQueued.position()).isNull(),
            () -> assertThat(notQueued.estimatedWaitSeconds()).isNull(),
            () -> assertThat(notQueued.recommendedPollingIntervalSeconds()).isNull(),
            () -> assertThat(notQueued.token()).isNull()
        );
    }

    private static class FakeOrderQueueRepository implements OrderQueueRepository {
        private final List<String> waitingUserIds = new ArrayList<>();
        private final Map<String, String> tokens = new HashMap<>();
        private long sequence = 0L;

        @Override
        public Entry enter(String userId) {
            if (!waitingUserIds.contains(userId)) {
                waitingUserIds.add(userId);
            }
            long rank = waitingUserIds.indexOf(userId) + 1L;
            return new Entry(userId, ++sequence, rank);
        }

        @Override
        public Optional<Long> rank(String userId) {
            int index = waitingUserIds.indexOf(userId);
            return index < 0 ? Optional.empty() : Optional.of(index + 1L);
        }

        @Override
        public long waitingCount() {
            return waitingUserIds.size();
        }

        @Override
        public String issueToken(String userId, Duration ttl) {
            String token = UUID.randomUUID().toString();
            tokens.put(userId, token);
            return token;
        }

        @Override
        public Optional<String> findToken(String userId) {
            return Optional.ofNullable(tokens.get(userId));
        }

        @Override
        public boolean isValidToken(String userId, String token) {
            return findToken(userId)
                .map(token::equals)
                .orElse(false);
        }

        @Override
        public void deleteToken(String userId) {
            tokens.remove(userId);
        }

        @Override
        public List<Admitted> admitNext(int count, Duration ttl) {
            List<Admitted> admitted = new ArrayList<>();
            int limit = Math.min(count, waitingUserIds.size());
            for (int i = 0; i < limit; i++) {
                String userId = waitingUserIds.remove(0);
                admitted.add(new Admitted(userId, issueToken(userId, ttl)));
            }
            return admitted;
        }
    }
}
