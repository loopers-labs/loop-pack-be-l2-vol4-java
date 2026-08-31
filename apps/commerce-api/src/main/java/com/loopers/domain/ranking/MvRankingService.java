package com.loopers.domain.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.config.CacheConfig;
import com.loopers.domain.product.ProductCacheDto;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.ranking.MvActiveVersionJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MvRankingService {

    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;
    private final MvActiveVersionJpaRepository activeVersionRepository;
    private final ProductService productService;

    @Cacheable(cacheNames = CacheConfig.WEEKLY_RANKING_CACHE, key = "#yearWeek")
    public List<RankingInfo> getWeeklyRankedAll(int yearWeek) {
        return activeVersionRepository.findById("WEEKLY:" + yearWeek)
            .map(version -> buildWeeklyRankingInfos(yearWeek, version.getActiveBatchId()))
            .orElse(List.of());
    }

    @Cacheable(cacheNames = CacheConfig.MONTHLY_RANKING_CACHE, key = "#yearMonth")
    public List<RankingInfo> getMonthlyRankedAll(int yearMonth) {
        return activeVersionRepository.findById("MONTHLY:" + yearMonth)
            .map(version -> buildMonthlyRankingInfos(yearMonth, version.getActiveBatchId()))
            .orElse(List.of());
    }

    private List<RankingInfo> buildWeeklyRankingInfos(int yearWeek, long batchId) {
        List<MvProductRankWeeklyEntity> rows =
            weeklyRepository.findByYearWeekAndBatchIdOrderByRankingOrderAsc(yearWeek, batchId);
        List<RankingInfo> result = new ArrayList<>(rows.size());
        for (MvProductRankWeeklyEntity row : rows) {
            try {
                ProductCacheDto product = productService.getActiveSnapshot(row.getProductId());
                result.add(new RankingInfo(row.getRankingOrder(), row.getScore(),
                    product.id(), product.name(), product.brandName(), product.price()));
            } catch (CoreException e) {
                if (e.getErrorType() != ErrorType.NOT_FOUND) throw e;
            }
        }
        return result;
    }

    private List<RankingInfo> buildMonthlyRankingInfos(int yearMonth, long batchId) {
        List<MvProductRankMonthlyEntity> rows =
            monthlyRepository.findByPeriodMonthAndBatchIdOrderByRankingOrderAsc(yearMonth, batchId);
        List<RankingInfo> result = new ArrayList<>(rows.size());
        for (MvProductRankMonthlyEntity row : rows) {
            try {
                ProductCacheDto product = productService.getActiveSnapshot(row.getProductId());
                result.add(new RankingInfo(row.getRankingOrder(), row.getScore(),
                    product.id(), product.name(), product.brandName(), product.price()));
            } catch (CoreException e) {
                if (e.getErrorType() != ErrorType.NOT_FOUND) throw e;
            }
        }
        return result;
    }
}
