package com.loopers.application.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WaitingQueueFacadeIntegrationTest {

    private final WaitingQueueFacade waitingQueueFacade;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public WaitingQueueFacadeIntegrationTest(
        WaitingQueueFacade waitingQueueFacade,
        RedisCleanUp redisCleanUp
    ) {
        this.waitingQueueFacade = waitingQueueFacade;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열에 진입하면 순번 0과 전체 대기 인원 1을 받는다.")
    @Test
    void entersQueueAndReturnsPositionAndTotal() {
        // given

        // when
        QueueInfo info = waitingQueueFacade.enter(100L);

        // then
        assertThat(info.position()).isZero();
        assertThat(info.totalWaiting()).isEqualTo(1L);
    }

    @DisplayName("나중에 진입한 유저는 다음 순번을 받고, 전체 대기 인원은 진입 수만큼 늘어난다.")
    @Test
    void laterEntrantGetsNextPosition() {
        // given
        waitingQueueFacade.enter(100L);

        // when
        QueueInfo second = waitingQueueFacade.enter(200L);

        // then
        assertThat(second.position()).isEqualTo(1L);
        assertThat(second.totalWaiting()).isEqualTo(2L);
    }

    @DisplayName("대기 중인 유저의 순번을 조회하면 진입 순번과 전체 대기 인원을 받는다.")
    @Test
    void readsPositionForWaitingUser() {
        // given
        waitingQueueFacade.enter(100L);
        waitingQueueFacade.enter(200L);

        // when
        QueueInfo info = waitingQueueFacade.getPosition(100L);

        // then
        assertThat(info.position()).isZero();
        assertThat(info.totalWaiting()).isEqualTo(2L);
    }

    @DisplayName("대기열에 없는 유저의 순번을 조회하면 QUEUE_ENTRY_NOT_FOUND 예외가 발생한다.")
    @Test
    void throwsWhenUserNotInQueue() {
        // given
        waitingQueueFacade.enter(100L);

        // when
        CoreException exception = assertThrows(CoreException.class, () -> waitingQueueFacade.getPosition(999L));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_ENTRY_NOT_FOUND);
    }
}
