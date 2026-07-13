package com.loopers.queue.application;

import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.queue.domain.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueServiceTest {

    // interval 200ms, batch 35 → tps 175
    private final OrderQueueAdmissionProperties admissionProperties = new OrderQueueAdmissionProperties(200L, 35);
    private final OrderQueueEntryTokenProperties entryTokenProperties = new OrderQueueEntryTokenProperties(300L);
    private final WaitingQueueRepository waitingQueueRepository = mock(WaitingQueueRepository.class);
    private final EntryTokenStore entryTokenStore = mock(EntryTokenStore.class);
    private final QueueService sut = new QueueService(
            waitingQueueRepository, entryTokenStore, admissionProperties, entryTokenProperties);

    @Test
    @DisplayName("진입하면 대기열에 넣고 부여받은 순번을 반환한다")
    void givenUser_whenEnter_thenReturnsRank() {
        when(waitingQueueRepository.add(eq("user-1"), anyLong())).thenReturn(true);
        when(waitingQueueRepository.rank("user-1")).thenReturn(0L);

        QueueResult.Enter result = sut.enter("user-1");

        assertThat(result.position()).isEqualTo(0L);
    }

    @Test
    @DisplayName("진입 직후 스케줄러가 뽑아가 순번이 null 이어도 예외 없이 순번 0 을 반환한다")
    void givenPoppedRightAfterAdd_whenEnter_thenReturnsZeroWithoutNpe() {
        when(waitingQueueRepository.rank("user-1")).thenReturn(null); // add 직후 ZPOPMIN 으로 뽑혀나감

        QueueResult.Enter result = sut.enter("user-1");

        assertThat(result.position()).isEqualTo(0L);
    }

    @Test
    @DisplayName("이미 입장 토큰이 있는 유저가 진입하면 대기열에 다시 넣지 않고 순번 0 을 반환한다")
    void givenAdmittedUser_whenEnter_thenDoesNotReAddAndReturnsZero() {
        when(entryTokenStore.find("user-1")).thenReturn(Optional.of("tok-123"));

        QueueResult.Enter result = sut.enter("user-1");

        assertAll(
                () -> assertThat(result.position()).isEqualTo(0L),
                () -> verify(waitingQueueRepository, never()).add(eq("user-1"), anyLong())
        );
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

    @Test
    @DisplayName("입장한(토큰 발급된) 유저는 순번 조회 시 토큰과 순번 0을 받는다")
    void givenAdmittedUser_whenPosition_thenReturnsToken() {
        when(entryTokenStore.find("user-1")).thenReturn(Optional.of("tok-123"));

        QueueResult.Position result = sut.position("user-1");

        assertAll(
                () -> assertThat(result.token()).isEqualTo("tok-123"),
                () -> assertThat(result.position()).isEqualTo(0L)
        );
    }

    @Test
    @DisplayName("발급하면 batch 만큼 꺼내 각자 입장 토큰을 발급한다")
    void givenWaitingUsers_whenAdmit_thenIssuesTokens() {
        when(waitingQueueRepository.popFront(35)).thenReturn(List.of("user-1", "user-2"));

        int count = sut.admit();

        assertAll(
                () -> assertThat(count).isEqualTo(2),
                () -> verify(entryTokenStore).issue("user-1", Duration.ofSeconds(300)),
                () -> verify(entryTokenStore).issue("user-2", Duration.ofSeconds(300))
        );
    }
}
