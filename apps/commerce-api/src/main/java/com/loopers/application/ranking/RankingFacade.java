package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankedProduct;
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

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;

    public Page<RankingItemInfo> getRankings(LocalDate date, Pageable pageable) {
        List<RankedProduct> ranked = rankingService.getPage(date, pageable);
        long total = rankingService.count(date);

        List<Long> productIds = ranked.stream().map(RankedProduct::productId).toList();
        Map<Long, ProductModel> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        long baseRank = (long) pageable.getPageNumber() * pageable.getPageSize();
        Map<Long, BrandModel> brandCache = new HashMap<>();
        List<RankingItemInfo> items = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RankedProduct rankedProduct = ranked.get(i);
            ProductModel product = productMap.get(rankedProduct.productId());
            if (product == null) {
                continue; // 소프트 삭제·부재 상품은 스킵. rank 는 ZSET 위치(스킵 전 index) 기준이라 남는 항목의 순위는 보존
            }
            BrandModel brand = brandCache.computeIfAbsent(product.getBrandId(), brandService::getBrand);
            items.add(RankingItemInfo.of(baseRank + i + 1, product, brand, rankedProduct.score()));
        }
        return new PageImpl<>(items, pageable, total);
    }

    public Long getRank(Long productId) {
        return rankingService.getRank(LocalDate.now(), productId)
                             .map(zeroBased -> zeroBased + 1)
                             .orElse(null);
    }
}