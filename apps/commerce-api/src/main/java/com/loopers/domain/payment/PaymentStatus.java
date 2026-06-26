package com.loopers.domain.payment;

/**
 * 결제 상태머신.
 *
 *   REQUESTED   → IN_PROGRESS  (PG 응답 PENDING, transactionKey 발급)
 *   REQUESTED   → UNKNOWN      (PG 호출 실패/timeout, 결과 모름)
 *   REQUESTED   → FAILED       (PG 영구 에러(4xx) 또는 라우팅 실패)
 *   IN_PROGRESS → SUCCESS      (콜백 또는 폴링)
 *   IN_PROGRESS → FAILED       (콜백 또는 폴링)
 *   UNKNOWN     → SUCCESS      (폴링으로 확정)
 *   UNKNOWN     → FAILED       (폴링으로 확정 또는 타임아웃)
 *   SUCCESS / FAILED 는 final.
 *
 *  UNKNOWN 이 핵심: "결제됐는지 안됐는지 모르는 상태" 를 명시적으로 분리해
 *  폴링/배치가 안전하게 확정할 수 있게 한다 (FAILED 로 단정해 돈을 잃는 케이스 차단).
 */
public enum PaymentStatus {
    REQUESTED,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    UNKNOWN,
    ;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED;
    }

    /** 폴링/타임아웃 처리 대상 여부. */
    public boolean needsReconciliation() {
        return this == IN_PROGRESS || this == UNKNOWN;
    }
}
