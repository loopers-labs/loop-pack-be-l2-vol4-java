package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.OrderQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class QueueDrainIntegrationTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    private OrderQueueRepository orderQueueRepository;
    @Autowired
    private EntryTokenRepository entryTokenRepository;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void enterInOrder(long... userIds) {
        long score = 1;
        for (long userId : userIds) {
            orderQueueRepository.enter(userId, score++);
        }
    }

    @DisplayName("대기열을 드레인하면, ")
    @Nested
    class IssueToNext {

        @DisplayName("앞에서 count 명을 꺼내 토큰을 발급하고, 발급된 userId 를 순서대로 반환한다.")
        @Test
        void issuesTokensForFrontUsers() {
            // arrange
            enterInOrder(1L, 2L, 3L, 4L, 5L);

            // act
            List<Long> issued = entryTokenRepository.issueToNext(3, TTL);

            // assert — 앞 3명 발급, 나머지는 큐에 남음
            assertThat(issued).containsExactly(1L, 2L, 3L);
            assertThat(entryTokenRepository.find(1L)).isPresent();
            assertThat(entryTokenRepository.find(2L)).isPresent();
            assertThat(entryTokenRepository.find(3L)).isPresent();
            assertThat(entryTokenRepository.find(4L)).isEmpty();
            assertThat(orderQueueRepository.size()).isEqualTo(2L);
        }

        @DisplayName("대기 인원보다 많이 요청해도, 있는 만큼만 발급되고 큐가 빈다.")
        @Test
        void issuesOnlyAvailable_whenCountExceedsQueue() {
            // arrange
            enterInOrder(1L, 2L);

            // act
            List<Long> issued = entryTokenRepository.issueToNext(18, TTL);

            // assert
            assertThat(issued).containsExactly(1L, 2L);
            assertThat(orderQueueRepository.size()).isEqualTo(0L);
        }

        @DisplayName("빈 대기열이면 아무에게도 발급되지 않는다.")
        @Test
        void issuesNothing_whenQueueEmpty() {
            List<Long> issued = entryTokenRepository.issueToNext(18, TTL);

            assertThat(issued).isEmpty();
        }
    }

    @DisplayName("배치 크기 이상의 대기자가 몰려도, ")
    @Nested
    class OverCapacity {

        @DisplayName("틱당 배치 크기까지만 방출되고, 전원이 유실·중복 없이 진입 순서대로 입장한다.")
        @Test
        void drainsSteadily_whenWaitersExceedBatchSize() {
            // arrange — 배치 크기(18)를 초과하는 45명이 대기
            int batchSize = 18;
            enterInOrder(LongStream.rangeClosed(1, 45).toArray());

            // act — 스케줄러 틱 1회
            List<Long> tick1 = entryTokenRepository.issueToNext(batchSize, TTL);

            // assert — 앞 18명만 방출, 초과분 27명은 순번이 당겨진 채 대기 유지
            assertThat(tick1).containsExactlyElementsOf(LongStream.rangeClosed(1, 18).boxed().toList());
            assertThat(orderQueueRepository.size()).isEqualTo(27L);
            assertThat(orderQueueRepository.findRank(19L)).contains(0L);

            // act — 남은 인원이 모두 소진될 때까지 틱 반복
            List<Long> tick2 = entryTokenRepository.issueToNext(batchSize, TTL);
            List<Long> tick3 = entryTokenRepository.issueToNext(batchSize, TTL);

            // assert — 유실·중복 없이 진입 순서 그대로 전원 입장, 큐 소진
            assertThat(tick2).containsExactlyElementsOf(LongStream.rangeClosed(19, 36).boxed().toList());
            assertThat(tick3).containsExactlyElementsOf(LongStream.rangeClosed(37, 45).boxed().toList());
            assertThat(orderQueueRepository.size()).isEqualTo(0L);
        }
    }

    @DisplayName("발급된 토큰은, ")
    @Nested
    class Ttl {

        @DisplayName("TTL 이 지나면 자동으로 만료된다.")
        @Test
        void expiresAfterTtl() {
            // arrange
            enterInOrder(1L);
            entryTokenRepository.issueToNext(1, Duration.ofSeconds(1));
            assertThat(entryTokenRepository.find(1L)).isPresent();

            // assert — TTL(1초) 경과 후 토큰이 사라진다
            await().atMost(3, TimeUnit.SECONDS)
                .until(() -> entryTokenRepository.find(1L).isEmpty());
        }
    }
}
