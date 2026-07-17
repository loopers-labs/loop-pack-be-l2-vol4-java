package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitingQueueServiceTest {

    private WaitingQueueService waitingQueueService;
    private WaitingQueueRepository waitingQueueRepository;

    @BeforeEach
    void setUp() {
        waitingQueueRepository = mock(WaitingQueueRepository.class);
        waitingQueueService = new WaitingQueueService(waitingQueueRepository);
    }

    @DisplayName("대기열에 진입할 때, ")
    @Nested
    class Enter {

        @DisplayName("Repository가 반환한 순번을 그대로 반환한다.")
        @Test
        void returnsRepositoryRank_whenUserEnters() {
            // given
            Long userId = 1L;
            WaitingQueueRank expectedRank = new WaitingQueueRank(42L);
            // enter()가 내부에서 Instant.now()를 캡처해 넘기므로 타임스탬프 값은 테스트에서 미리 알 수 없다 —
            // 이 위치에만 예외적으로 matcher를 쓴다(BrandServiceTest의 save(any(BrandModel.class))와 동일한 이유)
            when(waitingQueueRepository.enter(eq(userId), anyLong())).thenReturn(expectedRank);

            // when
            WaitingQueueRank result = waitingQueueService.enter(userId);

            // then
            assertThat(result).isEqualTo(expectedRank);
        }
    }

    @DisplayName("순번을 조회할 때, ")
    @Nested
    class GetRank {

        @DisplayName("Repository가 반환한 순번을 그대로 반환한다.")
        @Test
        void returnsRepositoryRank_whenRankIsRequested() {
            // given
            Long userId = 1L;
            WaitingQueueRank expectedRank = new WaitingQueueRank(7L);
            when(waitingQueueRepository.rank(userId)).thenReturn(expectedRank);

            // when
            WaitingQueueRank result = waitingQueueService.getRank(userId);

            // then
            assertThat(result).isEqualTo(expectedRank);
        }
    }

    @DisplayName("대기열 크기를 조회할 때, ")
    @Nested
    class Size {

        @DisplayName("Repository가 반환한 크기를 그대로 반환한다.")
        @Test
        void returnsRepositorySize_whenSizeIsRequested() {
            // given
            Long expectedSize = 100L;
            when(waitingQueueRepository.size()).thenReturn(expectedSize);

            // when
            Long result = waitingQueueService.size();

            // then
            assertThat(result).isEqualTo(expectedSize);
        }
    }
}
