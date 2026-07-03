package com.loopers.domain.payment;

/**
 * 결제 상태 머신.
 * <p>
 * 정상 흐름: PENDING → REQUESTED → SUCCEEDED
 * 실패 흐름: PENDING → REQUESTED → FAILED
 * 비정상 흐름: PENDING → TIMEOUT_PENDING (PG 응답 미수신) → SUCCEEDED / FAILED (복구 시점에 동기화)
 */
public enum PaymentStatus {
    /** 결제 레코드 생성됨. 아직 PG 호출 전. */
    PENDING,
    /** PG 가 요청을 접수함 (transactionKey 발급). 처리 결과 대기 중. */
    REQUESTED,
    /** PG 호출이 타임아웃/회로 차단 등으로 응답을 못 받음. 복구 스케줄러가 PG GET 으로 동기화. */
    TIMEOUT_PENDING,
    /** 결제 성공 (콜백 또는 복구 폴링으로 확정). */
    SUCCEEDED,
    /** 결제 실패 (한도 초과/잘못된 카드 등). */
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean isRecoverable() {
        return this == TIMEOUT_PENDING || this == REQUESTED;
    }
}
