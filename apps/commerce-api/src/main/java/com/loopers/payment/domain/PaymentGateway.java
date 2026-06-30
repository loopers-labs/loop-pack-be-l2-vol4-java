package com.loopers.payment.domain;

import java.util.List;

public interface PaymentGateway {

    PaymentGatewayResult request(PaymentGatewayCommand command);

    /** transactionKey 로 PG 의 현재 거래 상태를 조회한다(정합성 보정 — 키 기준 보정). */
    PaymentGatewayResult inquire(Long userId, String transactionKey);

    /** orderId 로 PG 에 엮인 거래 목록을 조회한다(정합성 보정 — 주문 기준 보정). 거래가 없으면 빈 목록. */
    List<PaymentGatewayResult> inquireByOrder(Long userId, String orderNumber);
}
