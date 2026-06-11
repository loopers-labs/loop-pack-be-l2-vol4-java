package com.loopers.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);

    Optional<StockModel> findByProductId(Long productId);

    List<StockModel> findAllByProductIds(Collection<Long> productIds);

    // 동시성 처리: quantity >= :quantity 조건의 원자적 UPDATE로 재고 차감. 반환값 0이면 재고 부족.
    int decreaseQuantity(Long productId, Long quantity);
}
