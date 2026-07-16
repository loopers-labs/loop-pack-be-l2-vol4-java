package com.loopers.application.ranking;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankedProductEntry;
import com.loopers.domain.ranking.RankingKeys;
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
 * 일간 랭킹 조회 유스케이스 — ZSET 페이지(productId+score)에 상품정보를 배치 조회로 aggregation 한다.
 * page 는 1-based (과제 스펙). 삭제/누락 상품은 표시에서 제외한다(순번은 ZSET 기준 유지 — 근사 허용).
 */
@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final int MAX_SIZE = 100;

    private final RankingQueryRepository rankingQueryRepository;
    private final ProductRepository productRepository;
    private final LikeCountRepository likeCountRepository;

    @Transactional(readOnly = true)
    public RankingPageInfo getRankings(String date, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다. (page>=1, 1<=size<=100)");
        }
        LocalDate targetDate = parseDate(date);
        long total = rankingQueryRepository.countRanked(targetDate);
        int offset = (page - 1) * size;
        List<RankedProductEntry> entries = rankingQueryRepository.findPage(targetDate, offset, size);
        if (entries.isEmpty()) {
            return new RankingPageInfo(DAY.format(targetDate), page, size, total, List.of());
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
        return new RankingPageInfo(DAY.format(targetDate), page, size, total, items);
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
