package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;

    public RankingPageInfo getRankings(LocalDate date, int page, int size) {
        String key = RankingKeyGenerator.dailyKey(date);
        long offset = (long) (page - 1) * size;

        List<RankingRepository.RankingEntry> entries = rankingRepository.getTopN(key, offset, (long) size);
        long totalElements = rankingRepository.getTotalCount(key);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        List<Long> productIds = entries.stream().map(RankingRepository.RankingEntry::productId).toList();
        Map<Long, ProductModel> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p, (existing, replacement) -> existing));

        List<RankingItemInfo> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingRepository.RankingEntry entry = entries.get(i);
            ProductModel product = productMap.get(entry.productId());
            if (product == null) {
                continue;
            }
            int rank = (int) offset + i + 1;
            items.add(new RankingItemInfo(rank, product.getId(), product.getName(), product.getPrice(), product.getBrandId(), entry.score()));
        }

        return new RankingPageInfo(items, totalElements, totalPages);
    }
}
