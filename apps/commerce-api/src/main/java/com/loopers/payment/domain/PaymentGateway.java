package com.loopers.payment.domain;

public interface PaymentGateway {

    PaymentGatewayResult request(PaymentGatewayCommand command);

    /** transactionKey 로 PG 의 현재 거래 상태를 조회한다(정합성 보정 — 키 보유 sweep). */
    PaymentGatewayResult inquire(Long userId, String transactionKey);
}
