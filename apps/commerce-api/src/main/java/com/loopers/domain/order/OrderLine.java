package com.loopers.domain.order;

import com.loopers.domain.product.vo.Price;

public record OrderLine(Long stockId, Long productId, String productName, Price price, int quantity) {

    public long amount() {
        return price.getValue() * quantity;
    }
}
