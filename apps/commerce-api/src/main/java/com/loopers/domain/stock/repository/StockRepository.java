package com.loopers.domain.stock.repository;

import com.loopers.domain.stock.model.Stock;

import java.util.Optional;

public interface StockRepository {
    Optional<Stock> findByProductId(Long productId);
    Stock save(Stock stock);
}
