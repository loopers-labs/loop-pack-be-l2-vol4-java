package com.loopers.ranking.application;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.ranking.domain.MonthlyProductRankModel;
import com.loopers.ranking.domain.ProductRankModel;
import com.loopers.ranking.domain.RankPeriod;
import com.loopers.ranking.domain.RankedEntry;
import com.loopers.ranking.domain.RankingKey;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.domain.WeeklyProductRankModel;
import com.loopers.ranking.infrastructure.MonthlyProductRankJpaRepository;
import com.loopers.ranking.infrastructure.WeeklyProductRankJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;
    private final WeeklyProductRankJpaRepository weeklyProductRankJpaRepository;
    private final MonthlyProductRankJpaRepository monthlyProductRankJpaRepository;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(RankPeriod period, LocalDate date, int page, int size) {
        return switch (period) {
            case DAILY -> getRankingsByKey(RankingKey.daily(date), page, size);
            case WEEKLY -> toRankingInfos(
                weeklyProductRankJpaRepository.findAllByOrderByRankAsc(PageRequest.of(page - 1, size)));
            case MONTHLY -> toRankingInfos(
                monthlyProductRankJpaRepository.findAllByOrderByRankAsc(PageRequest.of(page - 1, size)));
        };
    }

    @Transactional(readOnly = true)
    public List<RankingInfo> getHourlyRankings(int page, int size) {
        return getRankingsByKey(RankingKey.hourlyCurrent(), page, size);
    }

    private List<RankingInfo> getRankingsByKey(String key, int page, int size) {
        int offset = (page - 1) * size;
        List<RankedEntry> entries = rankingRepository.findPage(key, offset, size);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = entries.stream().map(RankedEntry::productId).toList();
        Map<Long, ProductModel> products = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankedEntry entry = entries.get(i);
            ProductModel product = products.get(entry.productId());
            // 랭킹엔 있으나 상품이 삭제된 경우는 건너뛴다. rank는 ZSET 위치(offset+i+1)를 그대로 쓴다.
            if (product == null) {
                continue;
            }
            result.add(new RankingInfo(
                offset + i + 1L,
                product.getId(),
                product.getName(),
                product.getPrice(),
                entry.score()
            ));
        }
        return result;
    }

    // MV(주간/월간)는 rank가 이미 적재돼 있어 offset 계산 없이 저장된 rank를 그대로 쓴다.
    private List<RankingInfo> toRankingInfos(List<? extends ProductRankModel> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = rows.stream().map(ProductRankModel::getProductId).toList();
        Map<Long, ProductModel> products = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<RankingInfo> result = new ArrayList<>();
        for (ProductRankModel row : rows) {
            ProductModel product = products.get(row.getProductId());
            // 랭킹엔 있으나 상품이 삭제된 경우는 건너뛴다.
            if (product == null) {
                continue;
            }
            result.add(new RankingInfo(
                row.getRank(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                row.getScore()
            ));
        }
        return result;
    }
}
