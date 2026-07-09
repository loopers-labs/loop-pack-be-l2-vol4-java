package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductMetricsRepository;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public List<Long> findActiveIdsPage(Long brandId, ProductSortType sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        List<ProductMetricsEntity> rows = (brandId == null)
                ? productMetricsJpaRepository.findByDeletedAtIsNull(pageable)
                : productMetricsJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId, pageable);
        return rows.stream().map(ProductMetricsEntity::getProductId).toList();
    }

    @Override
    public Map<Long, Long> findLikeCounts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productMetricsJpaRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductMetricsEntity::getProductId, ProductMetricsEntity::getLikeCount));
    }

    @Override
    public long getLikeCount(Long productId) {
        return productMetricsJpaRepository.findById(productId)
                .map(ProductMetricsEntity::getLikeCount)
                .orElse(0L);
    }

    /**
     * 정렬 + product_id DESC tiebreaker로 페이지 경계 안정성 보장 (01 §7.2).
     * 프로퍼티명은 product_metrics 엔티티 기준(likeCount/price/productId). 좋아요순 DESC 는 import.sql 의
     * idx_pm_(brand_)active_likes_desc 인덱스와 정렬 방향이 일치해 filesort 없이 처리된다.
     */
    private static Sort toSort(ProductSortType sort) {
        Sort byIdDesc = Sort.by(Sort.Direction.DESC, "productId");
        ProductSortType effective = (sort == null) ? ProductSortType.LATEST : sort;
        return switch (effective) {
            case LATEST -> byIdDesc;
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price").and(byIdDesc);
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "price").and(byIdDesc);
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount").and(byIdDesc);
        };
    }
}
