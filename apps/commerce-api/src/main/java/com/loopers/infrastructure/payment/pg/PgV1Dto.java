package com.loopers.infrastructure.payment.pg;

import java.util.List;

public class PgV1Dto {

    public record PaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    public record PaymentResponse(Meta meta, Data data) {
        public record Meta(String result, String errorCode, String message) {}
        public record Data(String transactionKey, String status, String reason) {}
    }

    /** GET /api/v1/payments?orderId= 응답 (주문에 엮인 결제건 목록). */
    public record OrderResponse(Meta meta, OrderData data) {
        public record Meta(String result, String errorCode, String message) {}
        public record OrderData(String orderId, List<Transaction> transactions) {}
        public record Transaction(String transactionKey, String status, String reason) {}
    }
}
