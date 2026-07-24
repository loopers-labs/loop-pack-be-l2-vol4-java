package com.loopers.application.ranking;

import com.loopers.application.brand.BrandRepository;
import com.loopers.application.product.ProductRepository;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public Page<RankingProductInfo> getRankings(
        RankingPeriod period,
        String startDate,
        String endDate,
        int page,
        int size
    ) {
        List<RankingEntry> rankingEntries = findRankingEntries(period, startDate, endDate, page, size);
        List<Long> productIds = rankingEntries.stream()
            .map(RankingEntry::productId)
            .toList();
        Map<Long, ProductModel> productMap = productRepository.findByIds(productIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        List<Long> brandIds = productMap.values().stream()
            .map(ProductModel::getBrandId)
            .distinct()
            .toList();
        Map<Long, String> brandNameMap = brandRepository.findByIds(brandIds).stream()
            .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        List<RankingProductInfo> content = rankingEntries.stream()
            .filter(entry -> productMap.containsKey(entry.productId()))
            .map(entry -> {
                ProductModel product = productMap.get(entry.productId());
                return new RankingProductInfo(
                    entry.rank(),
                    entry.score(),
                    product.getId(),
                    product.getName(),
                    product.getBrandId(),
                    brandNameMap.get(product.getBrandId()),
                    product.getPrice()
                );
            })
            .toList();

        return new PageImpl<>(
            content,
            PageRequest.of(Math.max(page - 1, 0), size),
            count(period, startDate, endDate)
        );
    }

    private List<RankingEntry> findRankingEntries(
        RankingPeriod period,
        String startDate,
        String endDate,
        int page,
        int size
    ) {
        if (period == RankingPeriod.DAILY) {
            return rankingRedisRepository.findRankings(startDate, page, size);
        }
        return rankingSnapshotRepository.findRankings(period, startDate, endDate, page, size);
    }

    private long count(RankingPeriod period, String startDate, String endDate) {
        if (period == RankingPeriod.DAILY) {
            return rankingRedisRepository.count(startDate);
        }
        return rankingSnapshotRepository.count(period, startDate, endDate);
    }
}
