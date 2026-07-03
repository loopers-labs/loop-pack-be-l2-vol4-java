package com.loopers.domain.payment;

/**
 * PG 결제 요청 커맨드(domain). cardNo 는 마스킹 전 원문이다 — PG 가 xxxx-xxxx-xxxx-xxxx 형식을 요구하므로
 * 저장용 마스킹값(PaymentModel.cardNo)이 아니라 요청 원문을 그대로 전달한다.
 * callbackUrl·X-USER-ID 는 인프라(어댑터)가 설정에서 주입하므로 커맨드에 두지 않는다.
 */
public record PgPaymentCommand(
        String orderNumber,
        CardType cardType,
        String cardNo,
        Long amount
) {
}
