package com.loopers.application.ordering.queue;

import com.loopers.domain.ordering.queue.OrderQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderQueueAdmissionWorkerTest {

    @DisplayName("설정된 배치 크기와 TTL로 대기열 앞 사용자에게 입장 토큰을 발급한다.")
    @Test
    void admitsNextUsersWithConfiguredBatchSizeAndTokenTtl() {
        // arrange
        FakeOrderQueueRepository orderQueueRepository = new FakeOrderQueueRepository();
        orderQueueRepository.enter("user1");
        orderQueueRepository.enter("user2");
        orderQueueRepository.enter("user3");
        OrderQueueAdmissionWorker worker = new OrderQueueAdmissionWorker(
            orderQueueRepository,
            Duration.ofMinutes(5),
            2
        );

        // act
        List<OrderQueueRepository.Admitted> admitted = worker.admitNext();

        // assert
        assertAll(
            () -> assertThat(admitted).extracting(OrderQueueRepository.Admitted::userId)
                .containsExactly("user1", "user2"),
            () -> assertThat(admitted).extracting(OrderQueueRepository.Admitted::token)
                .allSatisfy(token -> assertThat(token).isNotBlank()),
            () -> assertThat(orderQueueRepository.lastAdmitCount).isEqualTo(2),
            () -> assertThat(orderQueueRepository.lastTokenTtl).isEqualTo(Duration.ofMinutes(5)),
            () -> assertThat(orderQueueRepository.rank("user3")).contains(1L)
        );
    }

    @DisplayName("대기열이 비어 있으면 빈 입장 결과를 반환한다.")
    @Test
    void returnsEmptyList_whenQueueIsEmpty() {
        // arrange
        FakeOrderQueueRepository orderQueueRepository = new FakeOrderQueueRepository();
        OrderQueueAdmissionWorker worker = new OrderQueueAdmissionWorker(
            orderQueueRepository,
            Duration.ofMinutes(5),
            10
        );

        // act
        List<OrderQueueRepository.Admitted> admitted = worker.admitNext();

        // assert
        assertThat(admitted).isEmpty();
    }

    private static class FakeOrderQueueRepository implements OrderQueueRepository {
        private final List<String> waitingUserIds = new ArrayList<>();
        private int lastAdmitCount;
        private Duration lastTokenTtl;
        private long sequence;

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
            return UUID.randomUUID().toString();
        }

        @Override
        public Optional<String> findToken(String userId) {
            return Optional.empty();
        }

        @Override
        public boolean isValidToken(String userId, String token) {
            return false;
        }

        @Override
        public void deleteToken(String userId) {
        }

        @Override
        public List<Admitted> admitNext(int count, Duration ttl) {
            this.lastAdmitCount = count;
            this.lastTokenTtl = ttl;
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
