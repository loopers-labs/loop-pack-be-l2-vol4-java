package com.loopers.infrastructure.payment;

/** orderId는 PG가 6자 이상 문자열을 요구하므로 zero-pad해 보낸다. */
public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    long amount,
    String callbackUrl
) {

    /** cardNo(PAN)는 RestClient 오류 로깅 등에 평문 노출되지 않도록 끝 4자리만 남기고 마스킹한다. */
    @Override
    public String toString() {
        String maskedCardNo = cardNo == null || cardNo.length() < 4
            ? "****"
            : "****-****-****-" + cardNo.substring(cardNo.length() - 4);
        return "PgPaymentRequest{orderId=" + orderId
            + ", cardType=" + cardType
            + ", cardNo=" + maskedCardNo
            + ", amount=" + amount
            + ", callbackUrl=" + callbackUrl + "}";
    }
}
