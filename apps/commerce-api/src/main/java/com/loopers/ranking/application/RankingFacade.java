package com.loopers.ranking.application;

import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.ranking.RankingPeriod;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingReadService rankingReadService;
    private final ProductListQuery productListQuery;
    private final RankingMetrics rankingMetrics;

    public PageResult<RankingItemInfo> getRankings(
        RankingPeriod period,
        LocalDate date,
        int page,
        int size
    ) {
        PageResult<RankingPosition> rankingPage = getRankingPage(
            period,
            date,
            new PageQuery(page, size)
        );
        if (rankingPage.content().isEmpty()) {
            return withContent(rankingPage, List.of());
        }

        List<Long> productIds = rankingPage.content().stream()
            .map(RankingPosition::productId)
            .toList();
        Map<Long, ProductListInfo> productsById = findVisibleProducts(period, date, productIds).stream()
            .collect(Collectors.toMap(ProductListInfo::id, Function.identity()));
        List<RankingItemInfo> content = rankingPage.content().stream()
            .filter(position -> productsById.containsKey(position.productId()))
            .map(position -> new RankingItemInfo(position.rank(), productsById.get(position.productId())))
            .toList();

        return withContent(rankingPage, content);
    }

    private PageResult<RankingPosition> getRankingPage(
        RankingPeriod period,
        LocalDate date,
        PageQuery pageQuery
    ) {
        try {
            return rankingReadService.getRankings(period, date, pageQuery);
        } catch (DataAccessException e) {
            rankingMetrics.recordPageLookupFailure(period, RankingLookupStage.RANKING_LOOKUP);
            log.error(
                "Failed to look up ranking. period={}, date={}, page={}, size={}",
                period,
                date,
                pageQuery.page(),
                pageQuery.size(),
                e
            );
            throw rankingUnavailable();
        }
    }

    private List<ProductListInfo> findVisibleProducts(
        RankingPeriod period,
        LocalDate date,
        List<Long> productIds
    ) {
        try {
            return productListQuery.findVisibleProductsByIds(productIds);
        } catch (DataAccessException e) {
            rankingMetrics.recordPageLookupFailure(period, RankingLookupStage.PRODUCT_ENRICHMENT);
            log.error(
                "Failed to enrich ranking products. period={}, date={}, productIds={}",
                period,
                date,
                productIds,
                e
            );
            throw rankingUnavailable();
        }
    }

    private CoreException rankingUnavailable() {
        return new CoreException(
            ErrorType.SERVICE_UNAVAILABLE,
            "랭킹을 잠시 조회할 수 없습니다. 잠시 후 다시 시도해주세요."
        );
    }

    private PageResult<RankingItemInfo> withContent(
        PageResult<RankingPosition> rankingPage,
        List<RankingItemInfo> content
    ) {
        return new PageResult<>(
            content,
            rankingPage.totalElements(),
            rankingPage.totalPages(),
            rankingPage.number(),
            rankingPage.size(),
            rankingPage.first(),
            rankingPage.last()
        );
    }
}
