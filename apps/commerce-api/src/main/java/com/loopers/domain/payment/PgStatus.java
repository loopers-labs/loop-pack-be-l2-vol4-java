package com.loopers.domain.payment;

/**
 * PG 응답의 결제 상태. PG-Simulator 의 {@code TransactionStatus} 와 1:1 매핑된다.
 * 우리 내부 {@link PaymentStatus} 와는 다른 개념: PG 가 알려주는 처리 상태만 표현한다.
 *
 *  - PENDING : 요청 접수, 처리 중 (비동기 결제)
 *  - SUCCESS : 결제 승인
 *  - FAILED  : 결제 실패 (한도초과 / 잘못된 카드 등)
 */
public enum PgStatus {
    PENDING,
    SUCCESS,
    FAILED,
    ;
}
