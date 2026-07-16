package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 랭킹 페이지 조립: ZSET 에서 Top-N (id·score) 를 얻고, 상품정보는 DB(SSOT)에서 배치로 hydrate 해
 * ZSET 순서 그대로 병합한다. 삭제(누락)된 상품은 건너뛴다(guide 결정 #7).
 */
@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankingPage(LocalDate date, int page, int size) {
        RankingKey key = RankingKey.of(date);
        List<RankingEntry> entries = rankingRepository.topN(key, page, size);
        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();
        List<ProductModel> products = productRepository.findAllActiveByIds(productIds);
        return mergeInRankingOrder(entries, products, page, size);
    }

    private List<RankingInfo> mergeInRankingOrder(
        List<RankingEntry> entries, List<ProductModel> products, int page, int size
    ) {
        Map<Long, ProductModel> productById = products.stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        long baseRank = (long) page * size;
        return IntStream.range(0, entries.size())
            .filter(i -> productById.containsKey(entries.get(i).productId()))
            .mapToObj(i -> {
                RankingEntry entry = entries.get(i);
                return RankingInfo.of(baseRank + i + 1, productById.get(entry.productId()), entry.score());
            })
            .toList();
    }
}
