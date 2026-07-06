package com.loopers.queue.application;

import com.loopers.queue.domain.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueServiceTest {

    // interval 200ms, batch 35 → tps 175
    private final OrderQueueAdmissionProperties admissionProperties = new OrderQueueAdmissionProperties(200L, 35);
    private final WaitingQueueRepository waitingQueueRepository = mock(WaitingQueueRepository.class);
    private final QueueService sut = new QueueService(waitingQueueRepository, admissionProperties);

    @Test
    @DisplayName("진입하면 대기열에 넣고 부여받은 순번을 반환한다")
    void givenUser_whenEnter_thenReturnsRank() {
        when(waitingQueueRepository.add(eq("user-1"), anyLong())).thenReturn(true);
        when(waitingQueueRepository.rank("user-1")).thenReturn(0L);

        QueueResult.Enter result = sut.enter("user-1");

        assertThat(result.position()).isEqualTo(0L);
    }

    @Test
    @DisplayName("대기 중이면 순번·예상 대기시간·다음 폴링 간격을 반환한다")
    void givenWaitingUser_whenPosition_thenReturnsRankAndEstimates() {
        when(waitingQueueRepository.rank("user-1")).thenReturn(350L);

        QueueResult.Position result = sut.position("user-1");

        assertAll(
                () -> assertThat(result.position()).isEqualTo(350L),
                () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(2L),   // ceil(350/175)
                () -> assertThat(result.pollAfterMs()).isEqualTo(3000L)          // 순번 <1000 → 3s
        );
    }

    @Test
    @DisplayName("대기열에 없는 유저가 순번을 조회하면 예외를 던진다")
    void givenNotEnteredUser_whenPosition_thenThrows() {
        when(waitingQueueRepository.rank("user-1")).thenReturn(null);

        assertThatThrownBy(() -> sut.position("user-1"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("대기열");
    }
}
