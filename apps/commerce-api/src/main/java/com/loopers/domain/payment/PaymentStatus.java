package com.loopers.domain.payment;

/**
 * 내부(commerce) 결제 상태. PG의 상태(PgStatus)와 의도적으로 분리한다.
 */
public enum PaymentStatus {
    PENDING,      // 결제 생성됨, PG 접수 전. (요청 실패/타임아웃/폴백 시 여기 머무름 → 재시도·복구 대상)
    PROCESSING,   // PG 접수 완료(txKey 보유), 처리 결과 대기 중. (콜백 미수신 시 폴링 복구 대상)
    SUCCESS,      // 결제 성공 (종료 상태)
    FAILED;       // 결제 실패 (종료 상태)

    /** 더 이상 전이되면 안 되는 종료 상태인가 */
    public boolean isFinalized() {
        return this == SUCCESS || this == FAILED;
    }
}
