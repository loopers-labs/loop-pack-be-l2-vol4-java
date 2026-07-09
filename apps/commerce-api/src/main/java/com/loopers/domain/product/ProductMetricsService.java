package com.loopers.domain.product;

import com.loopers.support.page.PagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 상품 리스팅 read model(product_metrics) 조회 도메인 서비스. 좋아요순 정렬 id 페이지와 좋아요 수(단건/batch)를
 * 제공한다. 좋아요 수는 비동기 집계(commerce-streamer)로 갱신되는 결과적 일관성 값이다(week7 CQRS).
 */
@Component
@RequiredArgsConstructor
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    /** 활성 상품 id 목록(정렬 순서 보존) — 리스팅 정렬/필터/페이지 (UC-03). */
    @Transactional(readOnly = true)
    public List<Long> getActiveProductIdsPage(Long brandId, ProductSortType sort, int page, int size) {
        PagePolicy.validate(page, size);
        return productMetricsRepository.findActiveIdsPage(brandId, sort, page, size);
    }

    /** 좋아요 수 batch — 목록/내가 좋아요한 목록 조합용. 없는 id 는 0으로 보정해 채운다. */
    @Transactional(readOnly = true)
    public Map<Long, Long> getLikeCounts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productMetricsRepository.findLikeCounts(productIds);
    }

    /** 좋아요 수 단건 — 상세/단건 응답 조합용. */
    @Transactional(readOnly = true)
    public long getLikeCount(Long productId) {
        return productMetricsRepository.getLikeCount(productId);
    }
}
