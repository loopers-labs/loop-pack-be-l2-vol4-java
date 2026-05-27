package com.loopers.domain.stock;

import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);
}
