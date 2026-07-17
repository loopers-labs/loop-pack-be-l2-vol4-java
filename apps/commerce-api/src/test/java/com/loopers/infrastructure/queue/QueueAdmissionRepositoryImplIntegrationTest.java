package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueAdmissionRepository;
import com.loopers.domain.queue.QueueAdmissionRepository.AdmittedEntry;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class QueueAdmissionRepositoryImplIntegrationTest {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    @Autowired
    private QueueAdmissionRepository queueAdmissionRepository;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열에서 배치 발급을 할 때,")
    @Nested
    class AdmitBatch {

        @DisplayName("min(count, 대기 인원)명이 발급되고, 발급된 유저마다 토큰이 조회된다.")
        @Test
        void admitBatch_admitsMinOfCountAndWaitingSize_andIssuesTokenForEachAdmittedUser() {
            // given
            for (long userId = 1; userId <= 5; userId++) {
                waitingQueueRepository.enter(userId, userId * 1000);
            }

            // when
            List<AdmittedEntry> admitted = queueAdmissionRepository.admitBatch(3, TOKEN_TTL);

            // then
            assertThat(admitted).hasSize(3);
            admitted.forEach(entry -> assertThat(entryTokenRepository.find(entry.userId())).contains(entry.token()));
        }

        @DisplayName("발급된 토큰에는 요청한 TTL이 설정된다.")
        @Test
        void admitBatch_setsRequestedTtl_onIssuedTokens() {
            // given
            waitingQueueRepository.enter(1L, 1_000L);
            Duration requestedTtl = Duration.ofSeconds(30);

            // when
            List<AdmittedEntry> admitted = queueAdmissionRepository.admitBatch(1, requestedTtl);

            // then
            AdmittedEntry entry = admitted.get(0);
            Long remainingTtlSeconds = redisTemplate.getExpire(QueueRedisKeys.ENTRY_TOKEN_KEY_PREFIX + entry.userId());
            assertAll(
                    () -> assertThat(remainingTtlSeconds).isGreaterThan(0L),
                    () -> assertThat(remainingTtlSeconds).isLessThanOrEqualTo(requestedTtl.toSeconds())
            );
        }

        @DisplayName("발급된 유저는 대기열에서 사라진다.")
        @Test
        void admitBatch_removesAdmittedUsers_fromWaitingQueue() {
            // given
            for (long userId = 1; userId <= 3; userId++) {
                waitingQueueRepository.enter(userId, userId * 1000);
            }

            // when
            List<AdmittedEntry> admitted = queueAdmissionRepository.admitBatch(2, TOKEN_TTL);

            // then
            assertThat(waitingQueueRepository.size()).isEqualTo(1);
            admitted.forEach(entry -> assertThat(waitingQueueRepository.rank(entry.userId())).isNull());
        }

        @DisplayName("대기 인원이 count보다 적으면 있는 만큼만 발급한다.")
        @Test
        void admitBatch_admitsOnlyAvailableUsers_whenWaitingSizeIsLessThanCount() {
            // given
            waitingQueueRepository.enter(1L, 1_000L);
            waitingQueueRepository.enter(2L, 2_000L);

            // when
            List<AdmittedEntry> admitted = queueAdmissionRepository.admitBatch(10, TOKEN_TTL);

            // then
            assertThat(admitted).hasSize(2);
            assertThat(waitingQueueRepository.size()).isZero();
        }

        @DisplayName("동시에 여러 번 호출해도 유저가 중복 발급되지 않고, 대기 인원 전체가 정확히 한 번씩만 발급된다.")
        @Test
        void admitBatch_admitsEachWaitingUserExactlyOnce_whenCalledConcurrently() throws InterruptedException {
            // given
            int totalUsers = 50;
            int batchSize = 5;
            for (long userId = 1; userId <= totalUsers; userId++) {
                waitingQueueRepository.enter(userId, userId * 1000);
            }

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            List<AdmittedEntry> allAdmitted = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        while (waitingQueueRepository.size() > 0) {
                            List<AdmittedEntry> admitted = queueAdmissionRepository.admitBatch(batchSize, TOKEN_TTL);
                            if (admitted.isEmpty()) {
                                break;
                            }
                            allAdmitted.addAll(admitted);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // then
            List<Long> admittedUserIds = allAdmitted.stream().map(AdmittedEntry::userId).toList();
            assertAll(
                    () -> assertThat(allAdmitted).hasSize(totalUsers),
                    () -> assertThat(admittedUserIds).doesNotHaveDuplicates(),
                    () -> assertThat(waitingQueueRepository.size()).isZero(),
                    () -> allAdmitted.forEach(entry ->
                            assertThat(entryTokenRepository.find(entry.userId())).contains(entry.token()))
            );
        }
    }
}
