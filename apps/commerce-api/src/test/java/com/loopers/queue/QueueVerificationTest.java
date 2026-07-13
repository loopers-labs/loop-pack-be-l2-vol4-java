package com.loopers.queue;

import com.loopers.queue.application.OrderQueueAdmissionProperties;
import com.loopers.queue.application.QueueService;
import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.queue.domain.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class QueueVerificationTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;
    @Autowired
    private EntryTokenStore entryTokenStore;
    @Autowired
    private QueueService queueService;
    @Autowired
    private OrderQueueAdmissionProperties admissionProperties;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("동시 진입: N명이 동시에 진입해도 전원 정확히 한 번씩 순번(0..N-1)을 받는다")
    void givenConcurrentEnter_whenAllEnter_thenEachGetsDistinctRank() throws InterruptedException {
        int n = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            String userId = "user-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    queueService.enter(userId);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        Set<Long> ranks = IntStream.range(0, n)
                .mapToObj(i -> waitingQueueRepository.rank("user-" + i))
                .collect(java.util.stream.Collectors.toCollection(ConcurrentHashMap::newKeySet));

        assertAll(
                () -> assertThat(waitingQueueRepository.size()).isEqualTo(n),
                () -> assertThat(ranks).hasSize(n),                 // 중복·유실 없음
                () -> assertThat(ranks).containsExactlyInAnyOrderElementsOf(
                        IntStream.range(0, n).mapToObj(Long::valueOf).toList())  // 0..N-1 빠짐없이
        );
    }

    @Test
    @DisplayName("토큰 만료: TTL이 지나면 토큰이 무효화된다")
    void givenIssuedToken_whenTtlPassed_thenInvalidated() throws InterruptedException {
        entryTokenStore.issue("user-ttl", Duration.ofSeconds(1));
        assertThat(entryTokenStore.find("user-ttl")).isPresent();

        Thread.sleep(1500);

        assertThat(entryTokenStore.find("user-ttl")).isEmpty();
    }

    @Test
    @DisplayName("처리량 초과: batch 이상 몰려도 한 tick엔 batch명만 입장하고 나머지는 대기에 남는다")
    void givenMoreThanBatch_whenAdmitOnce_thenAdmitsBatchAndBuffersRest() {
        int batch = admissionProperties.batchSize();
        int total = batch * 3;
        for (int i = 0; i < total; i++) {
            waitingQueueRepository.add("t-" + i, i);
        }

        int admitted = queueService.admit();

        assertAll(
                () -> assertThat(admitted).isEqualTo(batch),                    // 한 tick = batch 상한
                () -> assertThat(waitingQueueRepository.size()).isEqualTo(total - batch)  // 나머지는 유실 없이 대기
        );
    }
}
