package com.loopers.application.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WaitingQueueFacadeIntegrationTest {

    private final WaitingQueueFacade waitingQueueFacade;
    private final EntryTokenRepository entryTokenRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public WaitingQueueFacadeIntegrationTest(
        WaitingQueueFacade waitingQueueFacade,
        EntryTokenRepository entryTokenRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.waitingQueueFacade = waitingQueueFacade;
        this.entryTokenRepository = entryTokenRepository;
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

    @DisplayName("대기 중인 유저의 순번을 조회하면 순번·전체 대기 인원·예상 대기를 받고, 토큰은 없다.")
    @Test
    void readsPositionForWaitingUser() {
        // given
        waitingQueueFacade.enter(100L);
        waitingQueueFacade.enter(200L);

        // when
        QueuePositionInfo info = waitingQueueFacade.getPosition(100L);

        // then
        assertThat(info.position()).isZero();
        assertThat(info.totalWaiting()).isEqualTo(2L);
        assertThat(info.estimatedWait()).isEqualTo("곧 주문 가능");   // 순번 0 = 곧 입장 차례
        assertThat(info.token()).isNull();                          // 아직 대기 중 → 토큰 없음
    }

    @DisplayName("입장된(토큰 보유) 유저의 순번을 조회하면 토큰과 '곧 주문 가능'을 받는다.")
    @Test
    void readsAdmittedStatusWithToken() {
        // given - 입장 처리되어 토큰만 보유(큐에는 없음)
        EntryToken issued = entryTokenRepository.issue(100L, Duration.ofMinutes(5));

        // when
        QueuePositionInfo info = waitingQueueFacade.getPosition(100L);

        // then
        assertThat(info.token()).isEqualTo(issued.value());
        assertThat(info.estimatedWait()).isEqualTo("곧 주문 가능");
        assertThat(info.position()).isZero();
    }

    @DisplayName("대기열에도 없고 토큰도 없는 유저의 순번을 조회하면 QUEUE_ENTRY_NOT_FOUND 예외가 발생한다.")
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
