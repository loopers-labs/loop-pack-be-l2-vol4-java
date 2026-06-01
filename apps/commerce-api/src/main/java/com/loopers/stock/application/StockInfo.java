package com.loopers.stock.application;

import com.loopers.stock.domain.StockModel;

public record StockInfo(Long id, Long productId, Integer totalStock, Integer reservedStock, Integer availableStock) {

    public static StockInfo from(StockModel model) {
        return new StockInfo(
            model.getId(),
            model.getProductId(),
            model.getTotalStock(),
            model.getReservedStock(),
            model.availableStock()
        );
    }
}
