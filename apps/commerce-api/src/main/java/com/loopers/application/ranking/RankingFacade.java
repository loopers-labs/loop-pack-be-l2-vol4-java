package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 랭킹 조회 — ZSET에서 상품 ID 페이지를 얻은 뒤 상품정보를 Aggregation 한다.
 */
@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final int MAX_PAGE_SIZE = 100;

    private final RankingRepository rankingRepository;
    private final ProductService productService;

    public RankingPageInfo getRankings(LocalDate date, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }

        long offset = (long) (page - 1) * size;
        List<Long> productIds = rankingRepository.findTopProductIds(date, offset, size);
        long totalCount = rankingRepository.countRanked(date);

        Map<Long, ProductInfo> productById = productService.getAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductInfo::id, Function.identity()));

        // ZSET이 준 순서(점수 내림차순)를 유지하며 상품정보를 결합 — 삭제된 상품은 목록에서 제외하되 순위 번호는 보존
        List<RankingPageInfo.RankedProduct> items = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            ProductInfo product = productById.get(productIds.get(i));
            if (product == null) {
                continue;
            }
            items.add(new RankingPageInfo.RankedProduct(offset + i + 1, product));
        }
        return new RankingPageInfo(date, page, size, totalCount, List.copyOf(items));
    }

    /** 오늘 랭킹판 기준 상품 순위(1-based). 순위에 없으면 empty — 상품 상세 조회에서 사용한다. */
    public Optional<Long> getTodayRank(Long productId) {
        return rankingRepository.findRank(LocalDate.now(), productId);
    }
}
