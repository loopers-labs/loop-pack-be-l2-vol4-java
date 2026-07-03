package com.loopers.application.payment;

/**
 * 폴링/수동 reconciliation 단건 처리 결과(설계 §6.3).
 */
public enum ReconcileOutcome {
    PAID,             // PG SUCCESS → PAID 확정
    FAILED,           // PG FAILED → FAILED 확정
    UNREACHED_FAILED, // 주문 없음(미도달) → FAILED 확정(자동 재요청 X)
    STILL_PROCESSING, // PG 처리 중 → 다음 주기 재확인
    ISOLATED          // grace 상한 초과 → UNKNOWN 격리
}
