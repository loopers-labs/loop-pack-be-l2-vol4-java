package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.ranking.PeriodRankingRepository;
import com.loopers.domain.ranking.RankingPeriod;
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
 * 랭킹 조회 — 기간별 저장소에서 상품 ID 페이지를 얻은 뒤 상품정보를 Aggregation 한다.
 * DAILY는 Redis ZSET(일자별), WEEKLY/MONTHLY는 배치가 적재한 MV(date로 해석한 스냅샷, 미지정 시 최신)에서 조회하며,
 * 이후 결합 로직은 공통이다.
 */
@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final int MAX_PAGE_SIZE = 100;

    private final RankingRepository rankingRepository;
    private final PeriodRankingRepository periodRankingRepository;
    private final ProductService productService;

    public RankingPageInfo getRankings(RankingPeriod period, LocalDate date, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }

        long offset = (long) (page - 1) * size;
        List<Long> productIds;
        long totalCount;
        LocalDate responseDate;
        if (period == RankingPeriod.DAILY) {
            LocalDate targetDate = date == null ? LocalDate.now() : date;
            productIds = rankingRepository.findTopProductIds(targetDate, offset, size);
            totalCount = rankingRepository.countRanked(targetDate);
            responseDate = targetDate;
        } else {
            // WEEKLY/MONTHLY: date를 포함하는 최신 스냅샷(date 없으면 최신)을 해석해 그 기간의 순위를 읽는다.
            Optional<PeriodRankingRepository.ResolvedPeriod> window = periodRankingRepository.resolvePeriod(period, date);
            if (window.isEmpty()) {
                return new RankingPageInfo(period, null, page, size, 0L, List.of());
            }
            PeriodRankingRepository.ResolvedPeriod resolved = window.get();
            productIds = periodRankingRepository.findTopProductIds(period, resolved, offset, size);
            totalCount = periodRankingRepository.countRanked(period, resolved);
            responseDate = resolved.periodEnd(); // 어느 스냅샷인지 알 수 있도록 기간 종료일을 응답 date로
        }

        Map<Long, ProductInfo> productById = productService.getAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductInfo::id, Function.identity()));

        // 저장소가 준 순서(순위)를 유지하며 상품정보를 결합 — 삭제된 상품은 목록에서 제외하되 순위 번호는 보존
        List<RankingPageInfo.RankedProduct> items = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            ProductInfo product = productById.get(productIds.get(i));
            if (product == null) {
                continue;
            }
            items.add(new RankingPageInfo.RankedProduct(offset + i + 1, product));
        }
        return new RankingPageInfo(period, responseDate, page, size, totalCount, List.copyOf(items));
    }

    /** 오늘 랭킹판 기준 상품 순위(1-based). 순위에 없으면 empty — 상품 상세 조회에서 사용한다. */
    public Optional<Long> getTodayRank(Long productId) {
        return rankingRepository.findRank(LocalDate.now(), productId);
    }
}
