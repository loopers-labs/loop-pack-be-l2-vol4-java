package com.loopers.domain.stock;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
    StockModel save(StockModel stock);

    Optional<StockModel> findByProductId(UUID productId);

    /**
     * 원자적 조건부 재고 예약.
     * UPDATE stocks SET reserved_quantity = reserved_quantity + qty
     *   WHERE product_id = :productId AND (total_quantity - reserved_quantity) >= qty
     *
     * @return affected rows — 0이면 재고 부족
     */
    int reserve(UUID productId, int qty);

    /**
     * 원자적 조건부 재고 총량 수정 (어드민).
     * UPDATE stocks SET total_quantity = :newTotal
     *   WHERE product_id = :productId AND :newTotal >= reserved_quantity
     *
     * @return affected rows — 0이면 reserved 미만으로 거부
     */
    int updateTotal(UUID productId, int newTotal);
}
