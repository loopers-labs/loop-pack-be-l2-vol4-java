package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRank;
import com.loopers.domain.queue.WaitingQueueRepository;
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
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class WaitingQueueRepositoryImplIntegrationTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열에 진입할 때,")
    @Nested
    class Enter {

        @DisplayName("첫 진입자는 0번째 순번을 받는다.")
        @Test
        void enter_returnsRankZero_whenUserIsFirstToEnter() {
            // given
            Long userId = 1L;

            // when
            WaitingQueueRank rank = waitingQueueRepository.enter(userId, 1_000L);

            // then
            assertThat(rank.value()).isZero();
        }

        @DisplayName("먼저 진입한 유저의 순번이 나중에 진입한 유저보다 작다.")
        @Test
        void enter_assignsSmallerRank_toEarlierEnteredUser() {
            // given
            Long firstUserId = 1L;
            Long secondUserId = 2L;

            // when
            WaitingQueueRank firstRank = waitingQueueRepository.enter(firstUserId, 1_000L);
            WaitingQueueRank secondRank = waitingQueueRepository.enter(secondUserId, 2_000L);

            // then
            assertThat(firstRank.value()).isLessThan(secondRank.value());
        }

        @DisplayName("이미 대기열에 있는 유저가 재진입하면, 최신 시각 기준으로 순번이 뒤로 밀린다.")
        @Test
        void enter_movesRankToBack_whenAlreadyWaitingUserReEnters() {
            // given
            Long firstUserId = 1L;
            Long secondUserId = 2L;
            waitingQueueRepository.enter(firstUserId, 1_000L);
            waitingQueueRepository.enter(secondUserId, 2_000L);

            // when
            WaitingQueueRank reEnteredRank = waitingQueueRepository.enter(firstUserId, 3_000L);

            // then
            assertThat(reEnteredRank.value()).isEqualTo(1L);
            assertThat(waitingQueueRepository.rank(secondUserId).value()).isZero();
        }

        @DisplayName("여러 유저가 동시에 진입해도 순번이 중복되거나 유실되지 않는다.")
        @Test
        void enter_assignsUniqueConsecutiveRanks_whenCalledConcurrently() throws InterruptedException {
            // given
            int userCount = 50;
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(userCount);

            // when
            for (long userId = 1; userId <= userCount; userId++) {
                long id = userId;
                executorService.submit(() -> {
                    try {
                        waitingQueueRepository.enter(id, System.currentTimeMillis());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // then
            List<Long> ranks = LongStream.rangeClosed(1, userCount)
                    .mapToObj(userId -> waitingQueueRepository.rank(userId).value())
                    .toList();
            List<Long> expectedRanks = LongStream.range(0, userCount).boxed().toList();
            assertAll(
                    () -> assertThat(waitingQueueRepository.size()).isEqualTo((long) userCount),
                    () -> assertThat(ranks).doesNotHaveDuplicates(),
                    () -> assertThat(ranks).containsExactlyInAnyOrderElementsOf(expectedRanks)
            );
        }
    }

    @DisplayName("대기열 크기를 조회할 때,")
    @Nested
    class Size {

        @DisplayName("현재 대기 중인 인원 수를 그대로 반환한다.")
        @Test
        void size_matchesNumberOfWaitingUsers() {
            // given
            waitingQueueRepository.enter(1L, 1_000L);
            waitingQueueRepository.enter(2L, 2_000L);
            waitingQueueRepository.enter(3L, 3_000L);

            // when
            Long size = waitingQueueRepository.size();

            // then
            assertThat(size).isEqualTo(3L);
        }
    }
}
