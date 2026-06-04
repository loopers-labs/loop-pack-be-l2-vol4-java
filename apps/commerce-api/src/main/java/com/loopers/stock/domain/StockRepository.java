package com.loopers.stock.domain;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);
    List<StockModel> findAllByProductIds(List<Long> productIds);
    List<StockModel> findAllByProductIdsWithLock(List<Long> productIds);
}
