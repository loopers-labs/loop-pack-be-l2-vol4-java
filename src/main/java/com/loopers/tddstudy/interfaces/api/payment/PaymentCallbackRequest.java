package com.loopers.tddstudy.interfaces.api.payment;

public record PaymentCallbackRequest(
        String transactionKey,
        String orderId,
        String status,
        String reason
){}
