package com.loopers.interfaces.api.payment.dto;

import com.loopers.application.payment.PaymentInfo;

public record PaymentV1Response(
    Long orderId,
    String status,
    String transactionKey,
    Long amount
) {
    public static PaymentV1Response from(PaymentInfo info) {
        return new PaymentV1Response(info.orderId(), info.status().name(), info.transactionKey(), info.amount());
    }
}
