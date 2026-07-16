package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WaitingQueueConcurrencyTest {

    private final WaitingQueueRepository waitingQueueRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public WaitingQueueConcurrencyTest(
        WaitingQueueRepository waitingQueueRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("서로 다른 N명이 동시에 진입해도 순번이 0..N-1 로 중복·누락 없이 유일하게 부여된다.")
    @Test
    void assignsUniquePositionsWithoutGapsUnderConcurrentEnroll() throws InterruptedException {
        // given
        int userCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);

        // when - 모든 스레드를 일제히 출발시켜 경합을 최대화
        for (long userId = 1; userId <= userCount; userId++) {
            long uid = userId;
            executor.submit(() -> {
                try {
                    start.await();
                    waitingQueueRepository.enroll(uid);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then - 전원 진입(누락 0) + 순번이 {0..N-1} 로 유일(중복 0)·연속(빠짐 0)
        assertThat(waitingQueueRepository.size()).isEqualTo((long) userCount);
        Set<Long> positions = LongStream.rangeClosed(1, userCount)
            .mapToObj(uid -> waitingQueueRepository.positionOf(uid).orElseThrow().value())
            .collect(Collectors.toSet());
        List<Long> expected = LongStream.range(0, userCount).boxed().toList();
        assertThat(positions).hasSize(userCount);
        assertThat(positions).containsExactlyInAnyOrderElementsOf(expected);
    }

    @DisplayName("같은 유저가 동시에 여러 번 진입해도 대기열엔 1건만 남고 순번은 0이다.")
    @Test
    void keepsSingleEntryWhenSameUserEnrollsConcurrently() throws InterruptedException {
        // given
        int attempts = 30;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);

        // when - 한 유저가 여러 기기/새로고침으로 동시에 진입 시도
        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    waitingQueueRepository.enroll(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then - ZADD NX 원자성으로 중복 진입이 자연히 막힌다
        assertThat(waitingQueueRepository.size()).isEqualTo(1L);
        assertThat(waitingQueueRepository.positionOf(100L)).contains(QueuePosition.of(0L));
    }
}
