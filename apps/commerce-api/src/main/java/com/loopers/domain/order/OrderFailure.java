package com.loopers.domain.order;

import com.loopers.support.error.ErrorType;

public record OrderFailure(
    Long productId,
    Integer quantity,
    ErrorType errorType,
    String reason
) {
}
