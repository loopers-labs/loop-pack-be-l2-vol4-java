package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MvRankingRepositoryImplTest {

    @Mock
    private MvProductRankWeeklyJpaRepository weeklyRepository;
    @Mock
    private MvProductRankMonthlyJpaRepository monthlyRepository;
    @InjectMocks
    private MvRankingRepositoryImpl sut;

    @Nested
    @DisplayName("MV 주기 라우팅 시")
    class Routing {

        @DisplayName("WEEKLY 조회는 주간 리포지토리로만 위임한다")
        @Test
        void weeklyRoutesToWeeklyRepository() {
            when(weeklyRepository.findByPeriodKeyOrderByRankNoAsc(anyString(), any())).thenReturn(List.of());

            sut.topProductIds(RankingPeriod.WEEKLY, "2026-W29", 0, 20);

            verify(weeklyRepository).findByPeriodKeyOrderByRankNoAsc(anyString(), any());
            verifyNoInteractions(monthlyRepository);
        }

        @DisplayName("MONTHLY 조회는 월간 리포지토리로만 위임한다")
        @Test
        void monthlyRoutesToMonthlyRepository() {
            when(monthlyRepository.countByPeriodKey(anyString())).thenReturn(3L);

            sut.size(RankingPeriod.MONTHLY, "2026-07");

            verify(monthlyRepository).countByPeriodKey(anyString());
            verifyNoInteractions(weeklyRepository);
        }
    }

    @Nested
    @DisplayName("MV가 지원하지 않는 주기가 들어오면")
    class UnsupportedPeriod {

        @DisplayName("DAILY 조회는 월간으로 새지 않고 예외를 던진다")
        @Test
        void dailyThrows() {
            assertThatThrownBy(() -> sut.topProductIds(RankingPeriod.DAILY, "2026-07-15", 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.size(RankingPeriod.DAILY, "2026-07-15"))
                .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(weeklyRepository, monthlyRepository);
        }

        @DisplayName("null 주기는 예외를 던진다")
        @Test
        void nullThrows() {
            assertThatThrownBy(() -> sut.topProductIds(null, "2026-07", 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sut.size(null, "2026-07"))
                .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(weeklyRepository, monthlyRepository);
        }
    }
}
