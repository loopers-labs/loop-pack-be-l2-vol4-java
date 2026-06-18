package com.loopers.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stock Aggregate 영속 포트. 동시성 제어를 위해 두 조회 경로를 제공한다.
 * - findByProductId: 일반 조회 (낙관적 락 경로 — @Version이 동시성 보장).
 * - findByProductIdForUpdate: 행 잠금 조회 (비관적 락, SELECT ... FOR UPDATE).
 */
public interface StockRepository {

    StockModel save(StockModel stock);

    Optional<StockModel> findByProductId(Long productId);

    /** 비관적 락(SELECT ... FOR UPDATE) — 경합 트랜잭션은 선행 커밋까지 대기한다. */
    Optional<StockModel> findByProductIdForUpdate(Long productId);

    /** 여러 상품의 재고 batch 조회 — 상품 목록 inStock 조합 N+1 회피. */
    List<StockModel> findByProductIds(Collection<Long> productIds);
}
