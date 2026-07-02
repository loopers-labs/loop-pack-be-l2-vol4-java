package com.loopers.domain.payment;

import com.loopers.domain.money.Money;

/**
 * "결제가 실패로 확정되었다"는 이미 일어난 사실을 통지하는 이벤트.
 * 재고·쿠폰 복구(보상)는 돈과 직결된 필수 처리라 이벤트가 아닌 confirm 트랜잭션 안에서 수행한다 —
 * 스프링 이벤트는 재시도가 없어 리스너 유실 시 복구가 영영 누락될 수 있기 때문. (Outbox 도입 후 이관 재검토)
 */
public record PaymentFailedEvent(Long orderId, Long userId, Money amount, String reason) {
    public static PaymentFailedEvent from(Payment payment) {
        return new PaymentFailedEvent(payment.getOrderId(), payment.getUserId(), payment.getAmount(), payment.getReason());
    }
}
