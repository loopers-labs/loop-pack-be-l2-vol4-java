package com.loopers.domain.payment;

/**
 * PG(외부 시스템)가 보고하는 결제 상태. 내부 PaymentStatus 와 분리한다.
 * - PENDING : PG가 접수했고 아직 처리 중
 * - SUCCESS : PG 처리 성공
 * - FAILED  : PG 처리 실패 (한도초과/잘못된카드 등 reason 참고)
 */
public enum PgStatus {
    PENDING,
    SUCCESS,
    FAILED
}
