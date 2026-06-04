package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;

/**
 * StockModel(순수 도메인) ↔ StockEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 */
public final class StockEntityMapper {

    private StockEntityMapper() {}

    public static StockEntity toEntity(StockModel stock) {
        return new StockEntity(stock.getProductId(), stock.getQuantity());
    }

    public static StockModel toDomain(StockEntity entity) {
        return StockModel.reconstitute(entity.getId(), entity.getProductId(), entity.getQuantity());
    }
}
