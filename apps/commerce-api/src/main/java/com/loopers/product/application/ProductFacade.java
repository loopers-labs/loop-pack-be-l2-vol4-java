package com.loopers.product.application;

import com.loopers.product.application.event.ProductEventPublisher;
import com.loopers.product.domain.ProductSort;
import com.loopers.ranking.application.RankingReadService;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductListQuery productListQuery;
    private final ProductDetailQuery productDetailQuery;
    private final ProductEventPublisher productEventPublisher;
    private final RankingReadService rankingReadService;
    private final ProductDetailMetrics productDetailMetrics;
    private final Clock clock;

    public PageResult<ProductListInfo> getProducts(int page, int size, Long brandId, String sort) {
        return productListQuery.findVisibleProducts(
            new PageQuery(page, size),
            brandId,
            ProductSort.from(sort)
        );
    }

    public ProductDetailResult getProduct(Long productId) {
        return getProduct(productId, null);
    }

    public ProductDetailResult getProduct(Long productId, Long userId) {
        ProductDetailInfo info = productDetailQuery.findVisibleProduct(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        Long rank = findTodayRank(productId);
        productEventPublisher.publishViewed(userId, info, ZonedDateTime.now(clock));
        return new ProductDetailResult(info, rank);
    }

    private Long findTodayRank(Long productId) {
        try {
            return rankingReadService.getDailyRank(LocalDate.now(clock), productId)
                .orElse(null);
        } catch (DataAccessException e) {
            productDetailMetrics.recordRankingLookupFailure();
            log.warn("Failed to look up today's ranking for productId={}", productId, e);
            return null;
        }
    }
}
