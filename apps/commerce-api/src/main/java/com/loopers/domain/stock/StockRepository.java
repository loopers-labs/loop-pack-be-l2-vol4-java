package com.loopers.domain.stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);
    List<StockModel> findAllByProductIds(List<Long> productIds);
    int decreaseStock(Long productId, int quantity);
}
