package com.loopers.application.ranking;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.MvRankingQueryRepository;
import com.loopers.domain.ranking.RankedProductEntry;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingQueryRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 랭킹 조회 유스케이스 — 기간에 따라 읽는 곳만 갈리고, 그 뒤 상품정보 aggregation 은 동일하다.
 * <ul>
 *   <li>일간: 실시간 ZSET (스트리머가 이벤트마다 갱신)</li>
 *   <li>주간/월간: 배치가 적재한 MV (사전 집계 — 조회 시점엔 계산하지 않는다)</li>
 * </ul>
 * page 는 1-based (과제 스펙). 삭제/누락 상품은 표시에서 제외한다(순번은 원본 기준 유지 — 근사 허용).
 */
@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final int MAX_SIZE = 100;

    private final RankingQueryRepository rankingQueryRepository;
    private final MvRankingQueryRepository mvRankingQueryRepository;
    private final ProductRepository productRepository;
    private final LikeCountRepository likeCountRepository;

    @Transactional(readOnly = true)
    public RankingPageInfo getRankings(String period, String date, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다. (page>=1, 1<=size<=100)");
        }
        RankingPeriod rankingPeriod = RankingPeriod.from(period);
        LocalDate targetDate = parseDate(date);
        String periodKey = rankingPeriod.periodKey(targetDate);
        int offset = (page - 1) * size;

        long total = countRanked(rankingPeriod, targetDate, periodKey);
        List<RankedProductEntry> entries = findPage(rankingPeriod, targetDate, periodKey, offset, size);
        if (entries.isEmpty()) {
            return new RankingPageInfo(rankingPeriod.name(), periodKey, page, size, total, List.of());
        }

        // 상품정보 aggregation — 배치 조회로 N+1 제거, 삭제 상품은 제외
        List<Long> ids = entries.stream().map(RankedProductEntry::productId).toList();
        Map<Long, Product> products = productRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Long> likeCounts = likeCountRepository.findAllByProductIds(ids).stream()
            .collect(Collectors.toMap(ProductLikeCount::getProductId, ProductLikeCount::getCount));

        List<RankingItemInfo> items = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            RankedProductEntry entry = entries.get(i);
            Product product = products.get(entry.productId());
            if (product == null) {
                continue; // 삭제/누락 상품 — 표시에서만 제외
            }
            items.add(new RankingItemInfo(
                offset + i + 1L,
                entry.productId(),
                product.getName(),
                product.getPrice(),
                likeCounts.getOrDefault(entry.productId(), 0L),
                entry.score()
            ));
        }
        return new RankingPageInfo(rankingPeriod.name(), periodKey, page, size, total, items);
    }

    private long countRanked(RankingPeriod period, LocalDate date, String periodKey) {
        return period == RankingPeriod.DAILY
            ? rankingQueryRepository.countRanked(date)
            : mvRankingQueryRepository.countRanked(period, periodKey);
    }

    private List<RankedProductEntry> findPage(
        RankingPeriod period, LocalDate date, String periodKey, int offset, int size) {
        return period == RankingPeriod.DAILY
            ? rankingQueryRepository.findPage(date, offset, size)
            : mvRankingQueryRepository.findPage(period, periodKey, offset, size);
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(RankingKeys.ZONE);
        }
        try {
            return LocalDate.parse(date, DAY);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 형식은 yyyyMMdd 입니다.");
        }
    }
}
