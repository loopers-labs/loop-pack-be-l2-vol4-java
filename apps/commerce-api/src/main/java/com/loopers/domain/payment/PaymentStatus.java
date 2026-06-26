package com.loopers.domain.payment;

/**
 * 결제(시도) 상태.
 *
 * <p>PG 가 비동기라 요청 직후엔 항상 {@link #PENDING} 이며,
 * 결과 확정(SUCCESS/FAILED)은 콜백 또는 상태 조회로만 이뤄진다.
 * 타임아웃은 실패가 아니라 "모름" 이므로 함부로 {@link #FAILED} 로 단정하지 않는다.</p>
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
