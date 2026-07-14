package com.loopers.infrastructure.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// 백그라운드 스케줄러가 3초마다 대기열을 pop하면 순번 검증이 비결정적이 되므로 비활성화
@SpringBootTest(properties = "queue.scheduler.enabled=false")
class WaitingQueueRedisRepositoryIntegrationTest {

    @Autowired private WaitingQueueRedisRepository repository;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("popMin() 호출 시,")
    @Nested
    class PopMin {

        @DisplayName("진입 순서대로 최대 count명을 꺼내고, 꺼낸 유저는 대기열에서 제거된다.")
        @Test
        void popsEarliestEntries_andRemovesThem() throws InterruptedException {
            // arrange
            repository.enter(1L, System.currentTimeMillis());
            Thread.sleep(5);
            repository.enter(2L, System.currentTimeMillis());
            Thread.sleep(5);
            repository.enter(3L, System.currentTimeMillis());

            // act
            List<Long> popped = repository.popMin(2);

            // assert
            assertAll(
                () -> assertThat(popped).containsExactly(1L, 2L),
                () -> assertThat(repository.findRank(1L)).isEmpty(),
                () -> assertThat(repository.findRank(2L)).isEmpty(),
                () -> assertThat(repository.findRank(3L)).isPresent(),
                () -> assertThat(repository.countWaiting()).isEqualTo(1L)
            );
        }

        @DisplayName("대기열 인원이 count보다 적으면 있는 만큼만 꺼낸다.")
        @Test
        void popsFewerThanCount_whenNotEnoughWaiting() {
            // arrange
            repository.enter(1L, System.currentTimeMillis());

            // act
            List<Long> popped = repository.popMin(5);

            // assert
            assertAll(
                () -> assertThat(popped).containsExactly(1L),
                () -> assertThat(repository.countWaiting()).isEqualTo(0L)
            );
        }

        @DisplayName("대기열이 비어있으면 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenQueueIsEmpty() {
            // act
            List<Long> popped = repository.popMin(5);

            // assert
            assertThat(popped).isEmpty();
        }
    }
}
