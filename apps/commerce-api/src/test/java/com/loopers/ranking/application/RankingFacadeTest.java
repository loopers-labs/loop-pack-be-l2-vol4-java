package com.loopers.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductFacade;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingPage;
import com.loopers.ranking.domain.RankingRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingFacadeTest {

    private final RankingRepository rankingRepository = mock(RankingRepository.class);
    private final ProductFacade productFacade = mock(ProductFacade.class);
    private final RankingFacade rankingFacade = new RankingFacade(rankingRepository, productFacade);

    @DisplayName("배치 조회 결과 순서와 무관하게 ZSET 순서대로 상품을 재조립한다.")
    @Test
    void preservesRankingOrderAfterBatchProductLookup() {
        LocalDate date = LocalDate.of(2026, 7, 16);
        ProductDetailInfo first = product(1L, "1위 상품");
        ProductDetailInfo second = product(2L, "2위 상품");
        when(rankingRepository.findPage(date, 1, 20))
                .thenReturn(
                        new RankingPage(
                                List.of(
                                        new RankingEntry(first.id(), 1L, 3.0D),
                                        new RankingEntry(second.id(), 2L, 2.0D)),
                                2L));
        when(productFacade.getExistingProductDetails(List.of(first.id(), second.id())))
                .thenReturn(List.of(second, first));

        RankingPageInfo result = rankingFacade.getRankings("20260716", 1, 20);

        assertThat(result.items())
                .extracting(item -> item.product().id())
                .containsExactly(first.id(), second.id());
        verify(productFacade).getExistingProductDetails(List.of(first.id(), second.id()));
    }

    private ProductDetailInfo product(Long id, String name) {
        return new ProductDetailInfo(id, 1L, "브랜드", name, "설명", 1000L, 10, 0L);
    }
}
