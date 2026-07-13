package com.loopers.queue.application;

import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.queue.domain.WaitingQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueDevServiceTest {

    private final WaitingQueueRepository waitingQueueRepository = mock(WaitingQueueRepository.class);
    private final EntryTokenStore entryTokenStore = mock(EntryTokenStore.class);
    private final OrderQueueEntryTokenProperties entryTokenProperties = new OrderQueueEntryTokenProperties(300L);
    private final QueueDevService sut = new QueueDevService(waitingQueueRepository, entryTokenStore, entryTokenProperties);

    @Test
    @DisplayName("더미 대기자 count 명을 대기열에 추가하고 전체 인원을 반환한다")
    void givenCount_whenFill_thenAddsDummiesAndReturnsSize() {
        when(waitingQueueRepository.size()).thenReturn(3L);

        long size = sut.fill(3);

        assertThat(size).isEqualTo(3L);
        verify(waitingQueueRepository).add(eq("dummy-1"), anyLong());
        verify(waitingQueueRepository).add(eq("dummy-2"), anyLong());
        verify(waitingQueueRepository).add(eq("dummy-3"), anyLong());
    }

    @Test
    @DisplayName("유저에게 입장 토큰을 즉시 발급한다")
    void givenUser_whenIssueToken_thenReturnsToken() {
        when(entryTokenStore.issue("user-1", Duration.ofSeconds(300))).thenReturn("tok-1");

        assertThat(sut.issueToken("user-1")).isEqualTo("tok-1");
    }
}
