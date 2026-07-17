package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    @Mock
    private RankingRepository rankingRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private RankingFacade rankingFacade;

    @DisplayName("getRankings는 ZSET 순위와 상품정보를 결합해 rank가 매겨진 목록을 반환한다.")
    @Test
    void getRankings_combinesRankingAndProductInfo() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = "ranking:all:20260717";
        when(rankingRepository.getTopN(key, 0L, 20L)).thenReturn(List.of(
            new RankingRepository.RankingEntry(20L, 9.0),
            new RankingRepository.RankingEntry(10L, 5.0)
        ));
        when(rankingRepository.getTotalCount(key)).thenReturn(2L);

        ProductModel productA = mock(ProductModel.class);
        when(productA.getId()).thenReturn(20L);
        when(productA.getName()).thenReturn("상품A");
        when(productA.getPrice()).thenReturn(10000L);
        when(productA.getBrandId()).thenReturn(1L);

        ProductModel productB = mock(ProductModel.class);
        when(productB.getId()).thenReturn(10L);
        when(productB.getName()).thenReturn("상품B");
        when(productB.getPrice()).thenReturn(20000L);
        when(productB.getBrandId()).thenReturn(2L);

        when(productRepository.findAllByIds(List.of(20L, 10L))).thenReturn(List.of(productA, productB));

        RankingPageInfo result = rankingFacade.getRankings(date, 1, 20);

        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(2);

        RankingItemInfo first = result.items().get(0);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.productId()).isEqualTo(20L);
        assertThat(first.productName()).isEqualTo("상품A");
        assertThat(first.price()).isEqualTo(10000L);
        assertThat(first.brandId()).isEqualTo(1L);
        assertThat(first.score()).isEqualTo(9.0);

        RankingItemInfo second = result.items().get(1);
        assertThat(second.rank()).isEqualTo(2);
        assertThat(second.productId()).isEqualTo(10L);
        assertThat(second.productName()).isEqualTo("상품B");
        assertThat(second.price()).isEqualTo(20000L);
        assertThat(second.brandId()).isEqualTo(2L);
        assertThat(second.score()).isEqualTo(5.0);
    }

    @DisplayName("productRepository에서 조회되지 않는 productId는 결과에서 제외하고, 남은 항목의 rank는 원래 순위를 유지한다.")
    @Test
    void getRankings_skipsMissingProductAndKeepsOriginalRank() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = "ranking:all:20260717";
        when(rankingRepository.getTopN(key, 0L, 20L)).thenReturn(List.of(
            new RankingRepository.RankingEntry(30L, 9.0),
            new RankingRepository.RankingEntry(20L, 7.0),
            new RankingRepository.RankingEntry(10L, 5.0)
        ));
        when(rankingRepository.getTotalCount(key)).thenReturn(3L);

        // 2번째 항목(productId 20L)만 productRepository에서 조회되지 않는 상황을 재현한다.
        // 1위(30L)와 3위(10L)는 정상적으로 조회된다.
        ProductModel productA = mock(ProductModel.class);
        when(productA.getId()).thenReturn(30L);
        when(productA.getName()).thenReturn("상품A");
        when(productA.getPrice()).thenReturn(9000L);
        when(productA.getBrandId()).thenReturn(1L);

        ProductModel productC = mock(ProductModel.class);
        when(productC.getId()).thenReturn(10L);
        when(productC.getName()).thenReturn("상품C");
        when(productC.getPrice()).thenReturn(30000L);
        when(productC.getBrandId()).thenReturn(3L);

        when(productRepository.findAllByIds(List.of(30L, 20L, 10L))).thenReturn(List.of(productA, productC));

        RankingPageInfo result = rankingFacade.getRankings(date, 1, 20);

        assertThat(result.items()).hasSize(2);

        RankingItemInfo first = result.items().get(0);
        assertThat(first.productId()).isEqualTo(30L);
        assertThat(first.rank()).isEqualTo(1);

        // 2위(productId 20L)는 제외되고, 원래 3위였던 상품(10L)의 rank는 2로 당겨지지 않고 3을 유지한다.
        RankingItemInfo survivor = result.items().get(1);
        assertThat(survivor.productId()).isEqualTo(10L);
        assertThat(survivor.rank()).isEqualTo(3);
        assertThat(survivor.productName()).isEqualTo("상품C");
        assertThat(survivor.price()).isEqualTo(30000L);
        assertThat(survivor.brandId()).isEqualTo(3L);
        assertThat(survivor.score()).isEqualTo(5.0);
    }

    @DisplayName("page=2, size=10이면 offset 10부터 조회한다.")
    @Test
    void getRankings_computesOffsetFromPage() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = "ranking:all:20260717";
        when(rankingRepository.getTopN(key, 10L, 10L)).thenReturn(List.of());
        when(rankingRepository.getTotalCount(key)).thenReturn(0L);

        rankingFacade.getRankings(date, 2, 10);

        org.mockito.Mockito.verify(rankingRepository).getTopN(key, 10L, 10L);
    }
}
