package com.loopers.domain.payment;

import com.loopers.domain.order.PaymentMethod;

/**
 * 외부 결제 시스템 추상화 (03 §2.6). 구현체는 infrastructure.payment.
 * pay()는 DB 트랜잭션 밖에서 호출 — 외부 호출이 DB 락을 잡지 않게 한다 (01 §7.6).
 */
public interface PaymentGateway {
    PaymentResult pay(Long orderId, Long amount, PaymentMethod method);
}
