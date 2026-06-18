package com.loopers.application.order;

/**
 * PENDING 주문 reconcile 결과 집계. scanned = 조회한 PENDING 건수,
 * paid/failed = PG 재조회로 확정된 건수, stillPending = 아직 미확정(TIMEOUT 유지),
 * skipped = 조회~확정 사이 다른 경로로 이미 상태가 바뀐(CONFLICT) 건수.
 */
public record ReconcileResult(int scanned, int paid, int failed, int stillPending, int skipped) {
}
