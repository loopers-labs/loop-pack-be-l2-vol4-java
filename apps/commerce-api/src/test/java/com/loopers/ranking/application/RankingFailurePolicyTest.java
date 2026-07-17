package com.loopers.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductFacade;
import com.loopers.product.application.ProductRankingFacade;
import com.loopers.product.application.RankedProductDetailInfo;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingFailurePolicyTest {

    @DisplayName("전용 랭킹 조회 중 Redis 장애가 발생하면 SERVICE_UNAVAILABLE로 변환한다.")
    @Test
    void throwsServiceUnavailableWhenRankingRedisFails() {
        RankingRepository rankingRepository = mock(RankingRepository.class);
        ProductFacade productFacade = mock(ProductFacade.class);
        when(rankingRepository.findPage(any(LocalDate.class), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("redis down"));
        RankingFacade rankingFacade = new RankingFacade(rankingRepository, productFacade);

        assertThatThrownBy(() -> rankingFacade.getRankings("20260716", 1, 20))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.SERVICE_UNAVAILABLE));
    }

    @DisplayName("시간 랭킹 조회 중 Redis 장애가 발생해도 SERVICE_UNAVAILABLE로 변환한다.")
    @Test
    void throwsServiceUnavailableWhenHourlyRankingRedisFails() {
        RankingRepository rankingRepository = mock(RankingRepository.class);
        ProductFacade productFacade = mock(ProductFacade.class);
        when(rankingRepository.findHourlyPage(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("redis down"));
        RankingFacade rankingFacade = new RankingFacade(rankingRepository, productFacade);

        assertThatThrownBy(() -> rankingFacade.getHourlyRankings("2026071623", 1, 20))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.SERVICE_UNAVAILABLE));
    }

    @DisplayName("상품 상세 랭킹 조회 중 Redis 장애가 발생하면 상품은 유지하고 rank만 null로 반환한다.")
    @Test
    void returnsNullRankWhenDetailRankingRedisFails() {
        ProductFacade productFacade = mock(ProductFacade.class);
        RankingRepository rankingRepository = mock(RankingRepository.class);
        ProductDetailInfo product =
                new ProductDetailInfo(
                        1L, 10L, "브랜드", "상품", "설명", 1000L, 5, 2L);
        when(productFacade.getProductDetail(1L)).thenReturn(product);
        when(rankingRepository.findRank(any(LocalDate.class), any(Long.class)))
                .thenThrow(new RuntimeException("redis down"));
        ProductRankingFacade detailFacade =
                new ProductRankingFacade(productFacade, rankingRepository);

        RankedProductDetailInfo result = detailFacade.getProductDetail(1L);

        assertThat(result.product()).isEqualTo(product);
        assertThat(result.rank()).isNull();
    }
}
