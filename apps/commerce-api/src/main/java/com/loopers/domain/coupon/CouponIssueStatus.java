package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 요청의 상태. 접수 시 PENDING 으로 저장되고, 발급 소비자(api 호스팅)가 처리 결과로 전이시킨다.
 * 클라이언트는 requestId 로 이 상태를 폴링한다.
 *
 * <ul>
 *   <li>{@link #PENDING} — 접수됨, 아직 발급 소비자가 처리하지 않음.</li>
 *   <li>{@link #SUCCESS} — 발급 성공.</li>
 *   <li>{@link #SOLD_OUT} — 한도 소진(issuedCount >= issueLimit)으로 발급 불가.</li>
 *   <li>{@link #ALREADY_ISSUED} — 이미 발급받은 유저의 재요청(1인 1매). 슬롯을 소모하지 않는다.</li>
 *   <li>{@link #FAILED} — 결정적 실패(예: 템플릿 삭제됨)로 재시도해도 성공할 수 없어 종료. 일시 장애는 재시도→DLQ 이며
 *       FAILED 로 전이하지 않는다(재시도로 복구되어야 하므로).</li>
 * </ul>
 */
public enum CouponIssueStatus {
    PENDING,
    SUCCESS,
    SOLD_OUT,
    ALREADY_ISSUED,
    FAILED
}
