package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 요청의 처리 상태 (Slice 4, Step3).
 *
 * <ul>
 *   <li>{@link #PENDING}  — 요청 접수(202). 아직 컨슈머가 처리 전.</li>
 *   <li>{@link #ISSUED}   — 수량 확보 성공 → user_coupon 발급 완료.</li>
 *   <li>{@link #SOLD_OUT} — 수량 소진(issued_count 도달) → 발급 실패(예외 아님).</li>
 *   <li>{@link #REJECTED} — 자격 미달 등으로 거절(현재는 미사용, 확장 여지).</li>
 * </ul>
 * 상태 전이(PENDING→ISSUED/SOLD_OUT)는 commerce-streamer 컨슈머가 발급 트랜잭션에서 확정한다.
 */
public enum CouponIssueRequestStatus {
    PENDING,
    ISSUED,
    SOLD_OUT,
    REJECTED
}
