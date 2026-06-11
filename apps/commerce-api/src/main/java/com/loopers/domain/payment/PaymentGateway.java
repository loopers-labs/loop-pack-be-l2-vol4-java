package com.loopers.domain.payment;

import java.util.Optional;

/**
 * 외부 결제 시스템(PG)과의 통신을 추상화하는 인터페이스.
 * 도메인은 이 인터페이스에만 의존하고, 실제 HTTP 호출 같은 구현 세부사항은 infrastructure 레이어에서 처리한다.
 *
 * <p><strong>인증 → 승인 2단계 모델</strong>: 결제창(인증)은 프론트와 PG 사이에서 일어나며
 * 서버는 관여하지 않는다. 인증이 끝나면 프론트가 paymentKey 를 들고 confirm API 를 호출하고,
 * 서버가 이 인터페이스의 {@link #confirm} 으로 PG 승인을 요청한다. 돈은 승인 시점에 움직인다.
 */
public interface PaymentGateway {

    /**
     * 결제 승인 요청 (server-to-server).
     *
     * @param paymentKey 결제창 인증 완료 시 PG 가 발급한 키
     * @param orderId    주문 id
     * @param amount     승인 금액 — PG 가 인증된 금액과 대조해 위변조를 차단한다
     */
    PaymentResult confirm(String paymentKey, Long orderId, Long amount);

    /**
     * 결제 상태 조회. 승인 응답을 받지 못한 경우(타임아웃/유실) 실제 결제 여부를 확인하는 용도.
     *
     * @return PG 에 결제 기록이 없으면 empty
     */
    Optional<PaymentResult> inquire(Long orderId);
}
