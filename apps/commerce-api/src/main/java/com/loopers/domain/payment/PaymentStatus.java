package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,      // 선저장 (PG 호출 전)
    IN_PROGRESS,  // PG 호출 완료, 콜백 대기 중
    SUCCESS,      // PG 처리 성공
    FAILED,       // PG 처리 실패 (원인은 failureCode 필드로 구분)
    ABORTED       // 타임아웃 후 망 취소 또는 강제 종료 처리
}
