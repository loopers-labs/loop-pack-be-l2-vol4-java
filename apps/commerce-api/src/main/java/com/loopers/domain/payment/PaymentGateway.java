package com.loopers.domain.payment;

/**
 * 외부 결제 시스템(PG)과의 통신을 추상화하는 인터페이스.
 * 도메인은 이 인터페이스에만 의존하고, 실제 HTTP 호출 같은 구현 세부사항은 infrastructure 레이어에서 처리한다.
 */
public interface PaymentGateway {
    PaymentResult request(Long orderId, Long amount);
}
