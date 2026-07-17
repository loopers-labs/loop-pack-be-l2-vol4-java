package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loopers.application.product.ProductDetailFacade;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
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
    ProductService productService = mock(ProductService.class);
    when(rankingRepository.findPage(any(LocalDate.class), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("redis down"));
    RankingFacade rankingFacade = new RankingFacade(rankingRepository, productService);

    assertThatThrownBy(() -> rankingFacade.getRankings("20260716", 1, 20))
        .isInstanceOfSatisfying(
            CoreException.class,
            exception ->
                assertThat(exception.getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE));
  }

  @DisplayName("시간 랭킹 조회 중 Redis 장애가 발생해도 동일하게 SERVICE_UNAVAILABLE로 변환한다.")
  @Test
  void throwsServiceUnavailableWhenHourlyRankingRedisFails() {
    RankingRepository rankingRepository = mock(RankingRepository.class);
    ProductService productService = mock(ProductService.class);
    when(rankingRepository.findHourlyPage(any(LocalDateTime.class), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("redis down"));
    RankingFacade rankingFacade = new RankingFacade(rankingRepository, productService);

    assertThatThrownBy(() -> rankingFacade.getHourlyRankings("2026071623", 1, 20))
        .isInstanceOfSatisfying(
            CoreException.class,
            exception ->
                assertThat(exception.getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE));
  }

  @DisplayName("상품 상세 랭킹 조회 중 Redis 장애가 발생하면 상품은 유지하고 rank만 null로 반환한다.")
  @Test
  void returnsNullRankWhenDetailRankingRedisFails() {
    ProductFacade productFacade = mock(ProductFacade.class);
    RankingRepository rankingRepository = mock(RankingRepository.class);
    ProductInfo product = new ProductInfo(1L, "상품", "설명", 1000L, 10, 1L, 0L);
    when(productFacade.getProduct(1L)).thenReturn(product);
    when(rankingRepository.findRank(any(LocalDate.class), any(Long.class)))
        .thenThrow(new RuntimeException("redis down"));
    ProductDetailFacade detailFacade = new ProductDetailFacade(productFacade, rankingRepository);

    ProductDetailInfo result = detailFacade.getProduct(1L);

    assertThat(result.product()).isEqualTo(product);
    assertThat(result.rank()).isNull();
  }
}
