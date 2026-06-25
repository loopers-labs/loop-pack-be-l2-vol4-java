package com.loopers.application.payment;

/**
 * PENDING 결제 reconcile 결과 집계 (03 §3.5). 콜백 유실로 PENDING에 남은 결제를 PG 진실원천으로
 * 재확인해 확정한 결과를 보고한다.
 * <ul>
 *   <li>{@code scanned} — 이번 회차에 조회한 PENDING 결제 건수</li>
 *   <li>{@code paid} — PG 결과가 SUCCESS여서 결제·주문을 확정한 건수</li>
 *   <li>{@code failed} — PG 결과가 FAILED여서 결제·주문을 실패 확정(재고·쿠폰 원복)한 건수</li>
 *   <li>{@code stillPending} — PG도 아직 미확정(또는 거래 미발견)이라 다음 회차로 미룬 건수</li>
 *   <li>{@code skipped} — 거래키 없는 고아 또는 조회~확정 사이 다른 경로가 먼저 확정한(경합) 건수</li>
 * </ul>
 */
public record PaymentReconcileResult(int scanned, int paid, int failed, int stillPending, int skipped) {
}
