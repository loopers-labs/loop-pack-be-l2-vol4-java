package com.loopers.domain.product;

public record StockDeductionResult(Long productId, String productName, Long price, int quantity) {}
