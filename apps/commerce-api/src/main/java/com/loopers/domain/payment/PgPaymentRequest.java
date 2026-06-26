package com.loopers.domain.payment;

/**
 * PG 결제 요청 파라미터 (도메인 언어). 어댑터가 pg-simulator 계약으로 변환한다.
 * <p>
 * cardNo는 <b>원본</b>(xxxx-xxxx-xxxx-xxxx)이어야 한다 — pg-simulator가 정규식으로 형식을 검증하므로
 * 마스킹된 값을 보내면 거부된다. (영속되는 PaymentModel.cardNo는 마스킹본, PG로 가는 값은 원본으로 분리)
 */
public record PgPaymentRequest(
        Long orderId,
        Long userId,
        CardType cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {
}
