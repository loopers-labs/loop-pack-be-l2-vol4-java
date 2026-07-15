package com.loopers.application.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("여러 유저가 순서대로 진입하면,")
    @Test
    void enter_assignsSequentialRank_byEntryOrder() throws InterruptedException {
        // act
        QueueInfo first = queueService.enter(1L);
        Thread.sleep(5);
        QueueInfo second = queueService.enter(2L);

        // assert
        assertThat(first.position()).isEqualTo(0L);
        assertThat(second.position()).isEqualTo(1L);
    }

    @DisplayName("동시에 여러 유저가 진입해도,")
    @Test
    void enter_guaranteesUniquePositions_underConcurrency() throws InterruptedException {
        // arrange
        int userCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(userCount);

        // act
        for (long userId = 1; userId <= userCount; userId++) {
            long id = userId;
            executor.submit(() -> {
                queueService.enter(id);
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();

        // assert
        Set<Long> positions = new HashSet<>();
        for (long userId = 1; userId <= userCount; userId++) {
            positions.add(queueService.position(userId).position());
        }
        assertThat(positions).hasSize(userCount);
    }
}
