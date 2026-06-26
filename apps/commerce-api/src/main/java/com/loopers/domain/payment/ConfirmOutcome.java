package com.loopers.domain.payment;

/**
 * {@link PaymentService#confirm} 의 결과. 후처리(주문 확정/실패)를 정확히 1회만 트리거하기 위한 신호다.
 *
 * <ul>
 *   <li>{@code PAID}/{@code FAILED} — 이번 호출이 조건부 UPDATE 의 승자(affected=1). 호출자가 주문 후처리를 1회 수행한다.</li>
 *   <li>{@code SKIPPED} — 이미 누군가 전이시킴(affected=0). 후처리 스킵(멱등).</li>
 *   <li>{@code ISOLATED} — 무결성 불일치로 UNKNOWN 격리. 주문 후처리 없음.</li>
 *   <li>{@code STILL_PENDING} — PG 결과가 아직 처리 중(PENDING). 전이 보류.</li>
 * </ul>
 */
public record ConfirmOutcome(Result result, Long orderId) {

    public enum Result {
        PAID, FAILED, SKIPPED, ISOLATED, STILL_PENDING
    }

    public static ConfirmOutcome paid(Long orderId) {
        return new ConfirmOutcome(Result.PAID, orderId);
    }

    public static ConfirmOutcome failed(Long orderId) {
        return new ConfirmOutcome(Result.FAILED, orderId);
    }

    public static ConfirmOutcome skipped(Long orderId) {
        return new ConfirmOutcome(Result.SKIPPED, orderId);
    }

    public static ConfirmOutcome isolated(Long orderId) {
        return new ConfirmOutcome(Result.ISOLATED, orderId);
    }

    public static ConfirmOutcome stillPending(Long orderId) {
        return new ConfirmOutcome(Result.STILL_PENDING, orderId);
    }
}
