package com.loopers.domain.payment;

/**
 * 외부 PG 에 결제를 요청할 때 도메인이 전달하는 값. HTTP/콜백 URL 같은 인프라 관심사는 포함하지 않는다.
 * (callbackUrl·엔드포인트 등은 어댑터가 설정에서 채운다.)
 */
public record PaymentGatewayCommand(
    Long userId,
    Long orderId,
    String cardType,
    String cardNo,
    Long amount
) {
}
