package com.loopers.domain.stock.repository;

import com.loopers.domain.stock.model.Stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    Optional<Stock> findByProductId(Long productId);
    List<Stock> findAllByProductIdIn(List<Long> productIds);
    Stock save(Stock stock);
}
