package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

/**
 * PG 연동 포트(domain). 구현(어댑터)은 infrastructure 에 두고 DIP 를 준수한다.
 * 요청 경로(POST)와 조회 경로(GET)는 서로 다른 CircuitBreaker 인스턴스로 보호된다(설계 §7.2).
 */
public interface PaymentGateway {

    /** 결제 요청 (CircuitBreaker:paymentRequest + Timeout + Retry 적용 지점). */
    PgTransaction request(PgPaymentCommand command);

    /** transactionKey 로 단건 조회. 없으면 빈 Optional(주문 없음). (CircuitBreaker:paymentQuery) */
    Optional<PgTransaction> findByTransactionKey(String transactionKey);

    /** orderNumber 로 조회(닻 되짚기). 없으면 빈 리스트(주문 없음). (CircuitBreaker:paymentQuery) */
    List<PgTransaction> findByOrderId(String orderNumber);
}
