package com.loopers.domain.stock;

public record StockDeductItem(Long productId, int quantity) {

    public static StockDeductItem of(Long productId, int quantity) {
        return new StockDeductItem(productId, quantity);
    }
}
