package com.loopers.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);
    Optional<StockModel> findByProductIdForUpdate(Long productId);
    List<StockModel> findAllByProductIdIn(Collection<Long> productIds);
    void deleteByProductId(Long productId);
}
