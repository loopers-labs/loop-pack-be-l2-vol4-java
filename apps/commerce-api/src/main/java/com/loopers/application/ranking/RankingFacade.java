package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductInfoAssembler;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingQueryCondition;
import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;
    private final ProductInfoAssembler productInfoAssembler;

    public RankingPageInfo getRankings(RankingQueryCondition condition) {
        List<RankingEntry> entries = rankingService.getRankings(condition);
        long totalElements = rankingService.countRankings(condition.date());

        List<Long> orderedIds = entries.stream().map(RankingEntry::productId).toList();
        Map<Long, ProductModel> productMap = productService.findAllByIds(orderedIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));
        List<ProductModel> orderedProducts = orderedIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ProductInfo> infoMap = productInfoAssembler.toInfoList(orderedProducts).stream()
                .collect(Collectors.toMap(ProductInfo::id, i -> i));

        List<RankingItemInfo> items = entries.stream()
                .filter(entry -> infoMap.containsKey(entry.productId()))
                .map(entry -> new RankingItemInfo(entry.rank(), entry.score(), infoMap.get(entry.productId())))
                .toList();

        return new RankingPageInfo(items, totalElements);
    }
}
