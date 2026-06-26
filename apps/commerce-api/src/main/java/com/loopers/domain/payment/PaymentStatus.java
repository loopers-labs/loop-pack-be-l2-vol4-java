package com.loopers.domain.payment;

/**
 * 결제 상태 머신.
 * <ul>
 *   <li>{@code PENDING} — 요청은 보냈으나 결과 모름(불확실).</li>
 *   <li>{@code PAID} — 성공 확정(terminal, 불변).</li>
 *   <li>{@code FAILED} — 실패 확정(한도/카드/미도달, terminal, 불변).</li>
 *   <li>{@code UNKNOWN} — 너무 오래 PENDING 이거나 무결성 불일치로 격리(운영자 확인 필요).</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING, PAID, FAILED, UNKNOWN;

    public boolean isTerminal() {
        return this == PAID || this == FAILED;
    }
}
