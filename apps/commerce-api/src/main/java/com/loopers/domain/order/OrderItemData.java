package com.loopers.domain.order;

import java.math.BigDecimal;

public record OrderItemData(Long productId, String productName, BigDecimal productPrice, Long quantity) {
}
