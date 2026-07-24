package com.loopers.ranking.application;

import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.ranking.RankingPeriod;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private RankingReadService rankingReadService;

    @Mock
    private ProductListQuery productListQuery;

    @Mock
    private RankingMetrics rankingMetrics;

    @InjectMocks
    private RankingFacade rankingFacade;

    @DisplayName("Ranking 상품 Page를 조회할 때")
    @Nested
    class GetRankings {

        @DisplayName("상품을 일괄 조회해 Ranking 순서로 복원하고 누락 상품의 원래 순위는 비워 둔다")
        @Test
        void restoresRankingOrderAndKeepsRankGaps_whenProductsAreMissingOrUnordered() {
            // arrange
            PageQuery pageQuery = new PageQuery(0, 3);
            PageResult<RankingPosition> positions = new PageResult<>(
                List.of(
                    new RankingPosition(1, 205L),
                    new RankingPosition(2, 101L),
                    new RankingPosition(3, 309L)
                ),
                3,
                1,
                0,
                3,
                true,
                true
            );
            ProductListInfo product101 = product(101L);
            ProductListInfo product309 = product(309L);
            when(rankingReadService.getRankings(RankingPeriod.WEEKLY, RANKING_DATE, pageQuery))
                .thenReturn(positions);
            when(productListQuery.findVisibleProductsByIds(List.of(205L, 101L, 309L)))
                .thenReturn(List.of(product309, product101));

            // act
            PageResult<RankingItemInfo> result = rankingFacade.getRankings(
                RankingPeriod.WEEKLY,
                RANKING_DATE,
                0,
                3
            );

            // assert
            assertAll(
                () -> assertThat(result.content()).containsExactly(
                    new RankingItemInfo(2, product101),
                    new RankingItemInfo(3, product309)
                ),
                () -> assertThat(result.totalElements()).isEqualTo(3),
                () -> assertThat(result.totalPages()).isEqualTo(1),
                () -> assertThat(result.number()).isZero(),
                () -> assertThat(result.size()).isEqualTo(3),
                () -> assertThat(result.first()).isTrue(),
                () -> assertThat(result.last()).isTrue()
            );
            verify(productListQuery).findVisibleProductsByIds(List.of(205L, 101L, 309L));
        }

        @DisplayName("Ranking이 비어 있으면 상품을 조회하지 않고 빈 Page를 반환한다")
        @Test
        void skipsProductQuery_whenRankingIsEmpty() {
            // arrange
            PageQuery pageQuery = new PageQuery(0, 20);
            PageResult<RankingPosition> positions = new PageResult<>(
                List.of(),
                0,
                0,
                0,
                20,
                true,
                true
            );
            when(rankingReadService.getRankings(RankingPeriod.MONTHLY, RANKING_DATE, pageQuery))
                .thenReturn(positions);

            // act
            PageResult<RankingItemInfo> result = rankingFacade.getRankings(
                RankingPeriod.MONTHLY,
                RANKING_DATE,
                0,
                20
            );

            // assert
            assertThat(result.content()).isEmpty();
            verifyNoInteractions(productListQuery);
        }

        @DisplayName("Redis Ranking 조회에 실패하면 503 Service Unavailable 예외를 반환한다")
        @Test
        void throwsServiceUnavailable_whenRedisRankingLookupFails() {
            // arrange
            PageQuery pageQuery = new PageQuery(0, 20);
            when(rankingReadService.getRankings(RankingPeriod.DAILY, RANKING_DATE, pageQuery))
                .thenThrow(new RedisConnectionFailureException("redis down"));

            // act & assert
            assertThatThrownBy(() -> rankingFacade.getRankings(
                RankingPeriod.DAILY,
                RANKING_DATE,
                0,
                20
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
            verify(rankingMetrics).recordPageLookupFailure(
                RankingPeriod.DAILY,
                RankingLookupStage.RANKING_LOOKUP
            );
            verifyNoInteractions(productListQuery);
        }

        @DisplayName("상품 정보 조회에 실패하면 503 Service Unavailable 예외를 반환한다")
        @Test
        void throwsServiceUnavailable_whenProductEnrichmentFails() {
            // arrange
            PageQuery pageQuery = new PageQuery(0, 20);
            PageResult<RankingPosition> positions = new PageResult<>(
                List.of(new RankingPosition(1, 205L)),
                1,
                1,
                0,
                20,
                true,
                true
            );
            when(rankingReadService.getRankings(RankingPeriod.WEEKLY, RANKING_DATE, pageQuery))
                .thenReturn(positions);
            when(productListQuery.findVisibleProductsByIds(List.of(205L)))
                .thenThrow(new DataRetrievalFailureException("database down"));

            // act & assert
            assertThatThrownBy(() -> rankingFacade.getRankings(
                RankingPeriod.WEEKLY,
                RANKING_DATE,
                0,
                20
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
            verify(rankingMetrics).recordPageLookupFailure(
                RankingPeriod.WEEKLY,
                RankingLookupStage.PRODUCT_ENRICHMENT
            );
        }
    }

    private ProductListInfo product(Long productId) {
        ProductListInfo product = mock(ProductListInfo.class);
        when(product.id()).thenReturn(productId);
        return product;
    }
}
