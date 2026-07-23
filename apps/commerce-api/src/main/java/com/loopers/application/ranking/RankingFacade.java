package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.PeriodRankedProduct;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;

    public Page<RankingItemInfo> getRankings(RankingPeriod period, LocalDate date, Pageable pageable) {
        if (period == RankingPeriod.DAILY) {
            List<PeriodRankedProduct> dailyRanked = dailyRanked(date, pageable);
            long dailyTotal = rankingService.count(date);
            return assemble(dailyRanked, pageable, dailyTotal);
        }
        List<PeriodRankedProduct> ranked = rankingService.getPeriodPage(period, date, pageable);
        long total = rankingService.countPeriod(period, date);
        return assemble(ranked, pageable, total);
    }

    public Long getRank(Long productId) {
        return rankingService.getRank(LocalDate.now(), productId)
                             .map(zeroBased -> zeroBased + 1)
                             .orElse(null);
    }

    /** 실시간(Redis) 랭킹은 순위를 저장하지 않으므로 ZSET 위치(page 오프셋 + index)로 rank 를 매긴다. */
    private List<PeriodRankedProduct> dailyRanked(LocalDate date, Pageable pageable) {
        List<RankedProduct> ranked = rankingService.getPage(date, pageable);
        long baseRank = (long) pageable.getPageNumber() * pageable.getPageSize();
        return IntStream.range(0, ranked.size())
                .mapToObj(i -> new PeriodRankedProduct(baseRank + i + 1, ranked.get(i).productId(), ranked.get(i).score()))
                .toList();
    }

    /** 순위·점수만 가진 랭킹 행에 상품·브랜드·좋아요 수를 조합한다. 일간/주간/월간 공통. */
    private Page<RankingItemInfo> assemble(List<PeriodRankedProduct> ranked, Pageable pageable, long total) {
        List<Long> productIds = ranked.stream().map(PeriodRankedProduct::productId).toList();
        Map<Long, ProductModel> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        Map<Long, BrandModel> brandCache = new HashMap<>();
        List<RankingItemInfo> items = new ArrayList<>();
        for (PeriodRankedProduct rankedProduct : ranked) {
            ProductModel product = productMap.get(rankedProduct.productId());
            if (product == null) {
                continue; // 소프트 삭제·부재 상품은 스킵. rank 는 스킵 전에 정해진 값이라 남는 항목의 순위는 보존
            }
            BrandModel brand = brandCache.computeIfAbsent(product.getBrandId(), brandService::getBrand);
            items.add(RankingItemInfo.of(rankedProduct.rank(), product, brand, rankedProduct.score()));
        }
        return new PageImpl<>(items, pageable, total);
    }
}