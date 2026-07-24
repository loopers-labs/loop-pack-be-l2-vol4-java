package com.loopers.product.application;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.event.ProductEventPublisher;
import com.loopers.ranking.application.RankingReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00[Asia/Seoul]");
    private static final Clock CLOCK = Clock.fixed(OCCURRED_AT.toInstant(), ZoneId.of("Asia/Seoul"));

    @Mock
    private ProductListQuery productListQuery;

    @Mock
    private ProductDetailQuery productDetailQuery;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private RankingReadService rankingReadService;

    @Mock
    private ProductDetailMetrics productDetailMetrics;

    private ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        productFacade = new ProductFacade(
            productListQuery,
            productDetailQuery,
            productEventPublisher,
            rankingReadService,
            productDetailMetrics,
            CLOCK
        );
    }

    @DisplayName("상품 상세를 조회할 때")
    @Nested
    class GetProduct {

        @DisplayName("상품 조회 성공 후 오늘의 순위와 상품 조회 이벤트를 함께 제공한다")
        @Test
        void returnsTodayRankAndPublishesProductViewedEvent_afterProductDetailIsFound() {
            // arrange
            ProductDetailInfo product = createProductDetailInfo();
            when(productDetailQuery.findVisibleProduct(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(rankingReadService.getDailyRank(LocalDate.of(2026, 6, 25), PRODUCT_ID))
                .thenReturn(Optional.of(3L));

            // act
            ProductDetailResult result = productFacade.getProduct(PRODUCT_ID, USER_ID);

            // assert
            assertThat(result).isEqualTo(new ProductDetailResult(product, 3L));
            verify(productEventPublisher).publishViewed(USER_ID, product, OCCURRED_AT);
        }

        @DisplayName("상품이 오늘 Ranking에 없으면 순위를 null로 반환한다")
        @Test
        void returnsNullRank_whenProductIsNotRankedToday() {
            // arrange
            ProductDetailInfo product = createProductDetailInfo();
            when(productDetailQuery.findVisibleProduct(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(rankingReadService.getDailyRank(LocalDate.of(2026, 6, 25), PRODUCT_ID))
                .thenReturn(Optional.empty());

            // act
            ProductDetailResult result = productFacade.getProduct(PRODUCT_ID, USER_ID);

            // assert
            assertThat(result.rank()).isNull();
        }

        @DisplayName("Ranking Redis 조회에 실패하면 상품 정보와 null 순위를 반환하고 실패를 기록한다")
        @Test
        void returnsProductWithNullRank_whenRankingRedisIsUnavailable() {
            // arrange
            ProductDetailInfo product = createProductDetailInfo();
            when(productDetailQuery.findVisibleProduct(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(rankingReadService.getDailyRank(LocalDate.of(2026, 6, 25), PRODUCT_ID))
                .thenThrow(new RedisConnectionFailureException("redis down"));

            // act
            ProductDetailResult result = productFacade.getProduct(PRODUCT_ID, USER_ID);

            // assert
            assertThat(result).isEqualTo(new ProductDetailResult(product, null));
            verify(productDetailMetrics).recordRankingLookupFailure();
            verify(productEventPublisher).publishViewed(USER_ID, product, OCCURRED_AT);
        }
    }

    private ProductDetailInfo createProductDetailInfo() {
        BrandInfo brand = new BrandInfo(1L, "Apple", "Premium device brand", OCCURRED_AT, OCCURRED_AT, null);
        return new ProductDetailInfo(
            PRODUCT_ID,
            brand,
            "iPhone 16 Pro",
            "High performance smartphone",
            1_550_000L,
            7L
        );
    }
}
