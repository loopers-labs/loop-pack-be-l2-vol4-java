package com.loopers.domain.stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);

    /** 비관적 쓰기 락으로 재고를 조회한다 (주문 시 동시성 제어용). */
    Optional<StockModel> findByProductIdForUpdate(Long productId);

    List<StockModel> findAllByProductIdIn(List<Long> productIds);
}
