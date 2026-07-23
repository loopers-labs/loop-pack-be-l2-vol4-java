package com.loopers.ranking.application;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.ranking.domain.RankPeriod;
import com.loopers.ranking.domain.RankedEntry;
import com.loopers.ranking.domain.RankingKey;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.infrastructure.MonthlyProductRankJpaRepository;
import com.loopers.ranking.infrastructure.WeeklyProductRankJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingFacadeTest {

    private RankingRepository rankingRepository;
    private ProductRepository productRepository;
    private RankingFacade rankingFacade;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        productRepository = mock(ProductRepository.class);
        rankingFacade = new RankingFacade(
            rankingRepository,
            productRepository,
            mock(WeeklyProductRankJpaRepository.class),
            mock(MonthlyProductRankJpaRepository.class)
        );
    }

    private ProductModel product(Long id, String name, Long price) {
        ProductModel p = mock(ProductModel.class);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getPrice()).thenReturn(price);
        return p;
    }

    @DisplayName("getRankings를 호출할 때,")
    @Nested
    class GetRankings {

        @DisplayName("ZSET 상위 productId 순서대로 상품정보가 조합되고 rank가 매겨진다.")
        @Test
        void aggregatesProductInfoInRankOrder() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 13);
            String key = RankingKey.daily(date);
            ProductModel p10 = product(10L, "A", 1000L);
            ProductModel p20 = product(20L, "B", 2000L);
            when(rankingRepository.findPage(key, 0, 20)).thenReturn(List.of(
                new RankedEntry(10L, 5.0),
                new RankedEntry(20L, 3.0)
            ));
            when(productRepository.findAllByIds(List.of(10L, 20L))).thenReturn(List.of(p10, p20));

            // act
            List<RankingInfo> result = rankingFacade.getRankings(RankPeriod.DAILY, date, 1, 20);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).rank()).isEqualTo(1L);
            assertThat(result.get(0).productId()).isEqualTo(10L);
            assertThat(result.get(0).name()).isEqualTo("A");
            assertThat(result.get(0).price()).isEqualTo(1000L);
            assertThat(result.get(0).score()).isEqualTo(5.0);
            assertThat(result.get(1).rank()).isEqualTo(2L);
            assertThat(result.get(1).productId()).isEqualTo(20L);
        }

        @DisplayName("2페이지면 offset이 (page-1)×size로 계산되고 rank도 이어진다.")
        @Test
        void appliesOffsetAndContinuesRank_whenSecondPage() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 13);
            String key = RankingKey.daily(date);
            ProductModel p30 = product(30L, "C", 3000L);
            when(rankingRepository.findPage(key, 20, 20)).thenReturn(List.of(
                new RankedEntry(30L, 1.0)
            ));
            when(productRepository.findAllByIds(List.of(30L))).thenReturn(List.of(p30));

            // act
            List<RankingInfo> result = rankingFacade.getRankings(RankPeriod.DAILY, date, 2, 20);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rank()).isEqualTo(21L);
            assertThat(result.get(0).productId()).isEqualTo(30L);
        }

        @DisplayName("ZSET이 비어있으면, 빈 목록을 반환하고 상품 조회도 하지 않는다.")
        @Test
        void returnsEmpty_whenNoRanking() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 13);
            when(rankingRepository.findPage(RankingKey.daily(date), 0, 20)).thenReturn(List.of());

            // act
            List<RankingInfo> result = rankingFacade.getRankings(RankPeriod.DAILY, date, 1, 20);

            // assert
            assertThat(result).isEmpty();
            verify(productRepository, never()).findAllByIds(any());
        }
    }
}
