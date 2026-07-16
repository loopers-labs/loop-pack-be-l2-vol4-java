package com.loopers.domain.queue;

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
class QueueStatusServiceIntegrationTest {

    private final QueueStatusService queueStatusService;
    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public QueueStatusServiceIntegrationTest(
        QueueStatusService queueStatusService,
        WaitingQueueRepository waitingQueueRepository,
        EntryTokenRepository entryTokenRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.queueStatusService = queueStatusService;
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열에 있는 유저는 순번과 전체 대기 인원을 담은 Waiting 상태를 받는다.")
    @Test
    void returnsWaitingWhenUserIsInQueue() {
        // given
        waitingQueueRepository.enroll(100L);
        waitingQueueRepository.enroll(200L);

        // when
        QueueStatus status = queueStatusService.statusOf(200L);

        // then
        assertThat(status).isInstanceOf(QueueStatus.Waiting.class);
        QueueStatus.Waiting waiting = (QueueStatus.Waiting) status;
        assertThat(waiting.position()).isEqualTo(QueuePosition.of(1L));
        assertThat(waiting.totalWaiting()).isEqualTo(2L);
    }

    @DisplayName("입장 토큰을 가진 유저는 토큰을 담은 Admitted 상태를 받는다.")
    @Test
    void returnsAdmittedWhenUserHasEntryToken() {
        // given
        EntryToken issued = entryTokenRepository.issue(100L, Duration.ofMinutes(5));

        // when
        QueueStatus status = queueStatusService.statusOf(100L);

        // then
        assertThat(status).isInstanceOf(QueueStatus.Admitted.class);
        assertThat(((QueueStatus.Admitted) status).token()).isEqualTo(issued);
    }

    @DisplayName("대기열에도 없고 토큰도 없는 유저의 상태 조회는 QUEUE_ENTRY_NOT_FOUND 로 거부된다.")
    @Test
    void throwsNotFoundWhenUserIsNeitherWaitingNorAdmitted() {
        // when
        CoreException exception = assertThrows(CoreException.class, () -> queueStatusService.statusOf(999L));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_ENTRY_NOT_FOUND);
    }

    @DisplayName("토큰과 대기열 항목이 모두 있으면 입장된 것으로 보고 Admitted 를 우선한다.")
    @Test
    void prefersAdmittedWhenBothTokenAndQueueEntryExist() {
        // given - 입장 후 재진입한 상황 (토큰은 남아있고 큐에도 다시 들어옴)
        entryTokenRepository.issue(100L, Duration.ofMinutes(5));
        waitingQueueRepository.enroll(100L);

        // when
        QueueStatus status = queueStatusService.statusOf(100L);

        // then
        assertThat(status).isInstanceOf(QueueStatus.Admitted.class);
    }
}
