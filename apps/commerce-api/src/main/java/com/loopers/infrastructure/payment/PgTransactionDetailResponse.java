package com.loopers.infrastructure.payment;

/**
 * pg-simulator 단건 상세 조회(GET /{transactionKey}) 응답. amount·cardNo 를 포함해 무결성 가드에 사용한다.
 */
public record PgTransactionDetailResponse(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
) {
}
