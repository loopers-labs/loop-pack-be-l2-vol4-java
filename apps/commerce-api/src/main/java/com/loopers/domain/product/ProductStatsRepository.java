package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductStatsRepository {
    ProductStatsModel save(ProductStatsModel productStats);

    Optional<ProductStatsModel> findByProduct(ProductModel product);

    List<ProductStatsModel> findAllByProductIds(List<Long> productIds);

    // 동시성 안전: 원자적 UPDATE로 처리하여 race condition 없이 카운트 정합성을 보장한다.
    void increaseLikeCount(Long productId);

    // 동시성 안전: 원자적 UPDATE로 처리하여 race condition 없이 카운트 정합성을 보장한다.
    void decreaseLikeCount(Long productId);

    Page<ProductStatsModel> findPageOrderByLikeCountDesc(Pageable pageable);

    Page<ProductStatsModel> findPageByProductIdsOrderByLikeCountDesc(List<Long> productIds, Pageable pageable);
}
