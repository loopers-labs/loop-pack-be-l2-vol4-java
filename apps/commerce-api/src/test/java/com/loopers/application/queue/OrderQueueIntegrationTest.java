package com.loopers.application.queue;

import com.loopers.domain.queue.OrderQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderQueueIntegrationTest {

    @Autowired
    private OrderQueueFacade orderQueueFacade;
    @Autowired
    private OrderQueueRepository orderQueueRepository;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열에 진입하면, ")
    @Nested
    class Enter {

        @DisplayName("진입 순서대로 1-based 순번이 부여된다.")
        @Test
        void assignsPositionInEntryOrder() {
            // act
            long first = orderQueueFacade.enter(1L).position();
            long second = orderQueueFacade.enter(2L).position();
            long third = orderQueueFacade.enter(3L).position();

            // assert
            assertThat(first).isEqualTo(1L);
            assertThat(second).isEqualTo(2L);
            assertThat(third).isEqualTo(3L);
        }

        @DisplayName("같은 유저가 다시 진입해도 순번과 대기 인원이 유지된다. (ZADD NX)")
        @Test
        void keepsPositionAndSize_whenSameUserEntersAgain() {
            // arrange
            orderQueueFacade.enter(1L);
            orderQueueFacade.enter(2L);

            // act
            long reentered = orderQueueFacade.enter(1L).position();

            // assert
            assertThat(reentered).isEqualTo(1L);
            assertThat(orderQueueFacade.position(1L).position()).isEqualTo(1L);
            assertThat(orderQueueRepository.size()).isEqualTo(2L);
        }

        @DisplayName("진입하지 않은 유저의 순번은 0이다.")
        @Test
        void returnsZero_whenNotInQueue() {
            assertThat(orderQueueFacade.position(999L).position()).isEqualTo(0L);
        }
    }

    @DisplayName("여러 유저가 동시에 진입해도, ")
    @Nested
    class Concurrency {

        @DisplayName("유실 없이 전원 등록되고 순번이 1..N 으로 유니크하게 부여된다.")
        @Test
        void assignsUniquePositionsWithoutLoss_whenEnteredConcurrently() throws Exception {
            // arrange
            int userCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(userCount);

            // act
            for (long userId = 1; userId <= userCount; userId++) {
                long id = userId;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderQueueFacade.enter(id);
                    } catch (Throwable ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert — 전원 등록(유실 0) + 순번 1..N 유니크
            assertThat(orderQueueRepository.size()).isEqualTo((long) userCount);
            List<Long> positions = LongStream.rangeClosed(1, userCount)
                .mapToObj(userId -> orderQueueFacade.position(userId).position())
                .sorted()
                .toList();
            assertThat(positions).isEqualTo(LongStream.rangeClosed(1, userCount).boxed().toList());
        }
    }
}
