package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EntryTokenSchedulerTest {

    @InjectMocks private EntryTokenScheduler scheduler;
    @Mock private WaitingQueueRepository waitingQueueRepository;
    @Mock private EntryTokenRepository entryTokenRepository;

    @DisplayName("입장 토큰 발급 스케줄러 실행 시,")
    @Test
    void issuesTokens_forEachPoppedUser() {
        // arrange
        given(waitingQueueRepository.popMin(QueuePolicy.BATCH_SIZE))
            .willReturn(List.of(1L, 2L, 3L));

        // act
        scheduler.issueEntryTokens();

        // assert
        then(entryTokenRepository).should(times(1)).issue(eq(1L), eq(QueuePolicy.TOKEN_TTL));
        then(entryTokenRepository).should(times(1)).issue(eq(2L), eq(QueuePolicy.TOKEN_TTL));
        then(entryTokenRepository).should(times(1)).issue(eq(3L), eq(QueuePolicy.TOKEN_TTL));
    }

    @DisplayName("대기열이 비어있으면 토큰을 발급하지 않는다.")
    @Test
    void issuesNothing_whenQueueEmpty() {
        // arrange
        given(waitingQueueRepository.popMin(QueuePolicy.BATCH_SIZE)).willReturn(List.of());

        // act
        scheduler.issueEntryTokens();

        // assert
        then(entryTokenRepository).should(never()).issue(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(Duration.class));
    }
}
