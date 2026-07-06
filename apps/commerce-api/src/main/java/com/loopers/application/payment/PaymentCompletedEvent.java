package com.loopers.application.payment;

/**
 * 결제 완료 이벤트 — 결제 확정과 부가작업(데이터 플랫폼 전송 등)의 경계를 나눈다.
 *
 * <p>{@link PaymentApplicationService#handleCallback} 의 SUCCESS 분기 한 곳에서만 발행된다.
 * 콜백·대사(reconcile) 두 경로 모두 handleCallback 을 통과하고, 맨 앞의 {@code isTerminal()} 가드가
 * 중복 발행을 막으므로 결제당 정확히 한 번 발행된다.
 *
 * <p>커밋 이후({@code AFTER_COMMIT}) 리스너가 소비하므로, 부가작업이 실패해도 결제 확정에는 영향이 없다.
 */
public record PaymentCompletedEvent(Long orderId, String transactionKey, Long amount) {
}
