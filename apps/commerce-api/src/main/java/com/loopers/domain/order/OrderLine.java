package com.loopers.domain.order;

import com.loopers.domain.quantity.Quantity;

public record OrderLine(Long productId, Quantity quantity) {}
