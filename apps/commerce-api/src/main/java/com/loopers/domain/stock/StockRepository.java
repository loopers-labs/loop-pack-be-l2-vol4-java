package com.loopers.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository {
    StockModel save(StockModel stock);
    Optional<StockModel> findByProductId(Long productId);
    /** 차감 경로 전용 — SELECT ... FOR UPDATE 로 행 락을 잡는다. 조회 경로는 {@link #findByProductId} 사용. */
    Optional<StockModel> findByProductIdForUpdate(Long productId);
    List<StockModel> findAllByProductIdIn(Collection<Long> productIds);
}
