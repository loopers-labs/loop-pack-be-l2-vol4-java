package com.loopers.domain.order;

import java.util.List;

public record OrderResult(
    Order order,
    List<OrderFailure> failures
) {
}
