package com.loopers.domain.payment;

import com.loopers.domain.order.PaymentMethod;

/**
 * 외부 결제 시스템 추상화 (03 §2.6). 구현체는 infrastructure.payment.
 * pay()는 DB 트랜잭션 밖에서 호출 — 외부 호출이 DB 락을 잡지 않게 한다 (01 §7.6).
 */
public interface PaymentGateway {
    PaymentResult pay(Long orderId, Long amount, PaymentMethod method);

    /**
     * 사후 결제 상태 재조회 (reconcile 용). pay()가 TIMEOUT으로 끝나 결과가 불확실한 주문에 대해
     * PG의 최종 결과(SUCCESS/FAILED/아직 미확정=TIMEOUT)를 다시 확인한다. DB 트랜잭션 밖에서 호출.
     */
    PaymentResult inquire(Long orderId);
}
