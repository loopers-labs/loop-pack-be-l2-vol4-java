package com.loopers.infrastructure.payment;

/** orderId는 PG가 6자 이상 문자열을 요구하므로 zero-pad해 보낸다. */
public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    long amount,
    String callbackUrl
) {

    /** cardNo(PAN)는 RestClient 오류 로깅 등에 노출되지 않도록 전부 마스킹한다. */
    @Override
    public String toString() {
        return "PgPaymentRequest{orderId=" + orderId
            + ", cardType=" + cardType
            + ", cardNo=****"
            + ", amount=" + amount
            + ", callbackUrl=" + callbackUrl + "}";
    }
}
