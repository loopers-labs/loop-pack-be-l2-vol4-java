package com.loopers.domain.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private RankingService rankingService;
    private RankingRepository rankingRepository;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        rankingService = new RankingService(rankingRepository);
    }

    @DisplayName("랭킹 페이지를 조회할 때,")
    @Nested
    class GetRankings {

        @DisplayName("조건의 start/end를 그대로 repository에 전달하고, 조회된 결과를 그대로 반환한다.")
        @Test
        void delegatesToRepository_withConditionRange() {
            // given
            RankingQueryCondition condition = new RankingQueryCondition(LocalDate.of(2026, 7, 16), 2, 20);
            List<RankingEntry> entries = List.of(new RankingEntry(1L, 21L, 12.5));
            when(rankingRepository.findRankings(condition.date(), condition.start(), condition.end())).thenReturn(entries);

            // when
            List<RankingEntry> result = rankingService.getRankings(condition);

            // then
            assertThat(result).isEqualTo(entries);
        }
    }

    @DisplayName("특정 상품의 순위를 조회할 때,")
    @Nested
    class GetRank {

        @DisplayName("repository가 반환한 순위를 그대로 반환한다.")
        @Test
        void returnsRepositoryResult() {
            // given
            LocalDate date = LocalDate.now();
            ProductRank rank = new ProductRank(2L);
            when(rankingRepository.findRank(date, 5L)).thenReturn(rank);

            // when
            ProductRank result = rankingService.getRank(date, 5L);

            // then
            assertThat(result).isEqualTo(rank);
        }

        @DisplayName("랭킹에 없는 상품이면 null을 반환한다.")
        @Test
        void returnsNull_whenProductNotRanked() {
            // given
            LocalDate date = LocalDate.now();
            when(rankingRepository.findRank(date, 999L)).thenReturn(null);

            // when
            ProductRank result = rankingService.getRank(date, 999L);

            // then
            assertThat(result).isNull();
        }
    }
}
