package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.MvRankingRepository;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 랭킹 ZSET에서 Top-N productId를 꺼내 상품정보를 aggregation한다.
 * ZSET이 점수 순서를, 상품/좋아요는 별도 조회로 살을 붙인다(단순 id가 아닌 상품정보 제공).
 */
@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final MvRankingRepository mvRankingRepository;
    private final ProductRepository productRepository;

    public Page<RankingInfo> getRankingPage(RankingPeriod period, LocalDate date, Integer hour, int page, int size) {
        long offset = (long) (page - 1) * size;
        PageRequest pageRequest = PageRequest.of(page - 1, size);

        // 일간은 Redis ZSET(실시간), 주간·월간은 배치가 적재한 MV에서 읽는다. 이후 상품정보 합성은 동일.
        List<Long> productIds;
        long total;
        if (period.isDaily()) {
            String key = RankingKeys.of(date, hour);
            productIds = rankingRepository.topProductIds(key, offset, size);
            total = rankingRepository.size(key);
        } else {
            String periodKey = period.mvPeriodKey(date);
            productIds = mvRankingRepository.topProductIds(period, periodKey, offset, size);
            total = mvRankingRepository.size(period, periodKey);
        }
        if (productIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageRequest, total);
        }

        Map<Long, ProductModel> productById = productRepository.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        Map<Long, Long> likeCounts = productRepository.likeCountsByProductIds(productIds);

        List<RankingInfo> content = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            ProductModel product = productById.get(productId);
            // 삭제된 상품은 건너뛴다 — 순위 번호는 ZSET 위치를 그대로 반영해 빈자리를 남긴다.
            if (product == null) {
                continue;
            }
            long rank = offset + i + 1;
            content.add(RankingInfo.of(rank, product, likeCounts.getOrDefault(productId, 0L)));
        }
        return new PageImpl<>(content, pageRequest, total);
    }
}
