package com.loopers.domain.product;

import java.util.Optional;

public interface ProductStockRepository {
    ProductStock save(ProductStock productStock);
    /**
     * 비관적 락(FOR UPDATE)을 걸고 조회한다.
     * 동시 주문 시 한 트랜잭션씩 순서대로 처리되도록 보장한다.
     */
    Optional<ProductStock> findByProductIdWithLock(Long productId);
    Optional<ProductStock> findByProductId(Long productId);
}
