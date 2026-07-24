package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class WaitingQueueServiceIntegrationTest {

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 진입 시,")
    @Nested
    class Enter {

        @DisplayName("처음 진입한 유저는 순번 0과 전체 인원 1을 받는다.")
        @Test
        void returnsRankZero_whenFirstToEnter() {
            // act
            QueuePosition position = waitingQueueService.enter(1L);

            // assert
            assertAll(
                () -> assertThat(position.rank()).isEqualTo(0L),
                () -> assertThat(position.total()).isEqualTo(1L)
            );
        }

        @DisplayName("두 명이 순서대로 진입하면 순번이 0, 1로 부여된다.")
        @Test
        void assignsSequentialRanks_whenEnteredInOrder() {
            // act
            QueuePosition first = waitingQueueService.enter(1L);
            QueuePosition second = waitingQueueService.enter(2L);

            // assert
            assertAll(
                () -> assertThat(first.rank()).isEqualTo(0L),
                () -> assertThat(second.rank()).isEqualTo(1L),
                () -> assertThat(second.total()).isEqualTo(2L)
            );
        }

        @DisplayName("이미 진입한 유저가 다시 진입해도 전체 인원과 순번이 유지된다(멱등).")
        @Test
        void isIdempotent_whenSameUserEntersAgain() {
            // arrange
            waitingQueueService.enter(1L);
            waitingQueueService.enter(2L);

            // act
            QueuePosition reentered = waitingQueueService.enter(1L);

            // assert
            assertAll(
                () -> assertThat(reentered.rank()).isEqualTo(0L),
                () -> assertThat(reentered.total()).isEqualTo(2L)
            );
        }
    }

    @DisplayName("순번 조회 시,")
    @Nested
    class GetPosition {

        @DisplayName("대기열에 있는 유저는 자신의 순번과 전체 인원을 조회할 수 있다.")
        @Test
        void returnsPosition_whenUserInQueue() {
            // arrange
            waitingQueueService.enter(1L);
            waitingQueueService.enter(2L);

            // act
            QueuePosition position = waitingQueueService.getPosition(2L);

            // assert
            assertAll(
                () -> assertThat(position.rank()).isEqualTo(1L),
                () -> assertThat(position.total()).isEqualTo(2L)
            );
        }

        @DisplayName("대기열에 없는 유저의 순번을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserNotInQueue() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                waitingQueueService.getPosition(999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("대기열 앞에서 N명을 꺼낼 때,")
    @Nested
    class PollFront {

        @DisplayName("가장 앞선 N명을 진입 순서대로 반환하고, 큐에서 제거한다.")
        @Test
        void returnsFrontUsersInOrder_andRemovesThem() {
            // arrange
            waitingQueueService.enter(1L);
            waitingQueueService.enter(2L);
            waitingQueueService.enter(3L);

            // act
            List<Long> polled = waitingQueueService.pollFront(2);

            // assert
            assertAll(
                () -> assertThat(polled).containsExactly(1L, 2L),
                // 꺼낸 뒤에는 뒤에 있던 3L만 남아 순번 0이 된다.
                () -> assertThat(waitingQueueService.getPosition(3L).rank()).isEqualTo(0L),
                () -> assertThat(waitingQueueService.getPosition(3L).total()).isEqualTo(1L)
            );
        }

        @DisplayName("대기 인원보다 많이 요청하면 있는 만큼만 반환하고 큐를 비운다.")
        @Test
        void returnsAllAvailable_whenRequestedMoreThanSize() {
            // arrange
            waitingQueueService.enter(1L);
            waitingQueueService.enter(2L);

            // act
            List<Long> polled = waitingQueueService.pollFront(10);

            // assert
            assertAll(
                () -> assertThat(polled).containsExactly(1L, 2L),
                () -> assertThat(waitingQueueService.pollFront(1)).isEmpty()
            );
        }

        @DisplayName("빈 대기열에서 꺼내면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenQueueEmpty() {
            // act & assert
            assertThat(waitingQueueService.pollFront(5)).isEmpty();
        }
    }
}
