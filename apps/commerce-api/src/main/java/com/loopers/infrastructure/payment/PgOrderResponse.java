package com.loopers.infrastructure.payment;

import java.util.List;

public record PgOrderResponse(
    String orderId,
    List<PgTransactionResponse> transactions
) {
}
