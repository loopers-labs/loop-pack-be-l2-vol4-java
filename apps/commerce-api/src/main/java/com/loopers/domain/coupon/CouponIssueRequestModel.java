package com.loopers.domain.coupon;

import com.loopers.support.tsid.TsidGenerator;

import java.time.ZonedDateTime;

/**
 * 선착순 쿠폰 발급 "요청" Aggregate 루트 — 순수 도메인 객체 (Slice 4, Step3).
 * API는 이 요청을 접수(PENDING)하고 즉시 202를 반환하며, 실제 발급은 commerce-streamer 컨슈머가 비동기로 확정한다.
 *
 * <p>식별자(requestId)는 접수 즉시 클라이언트에 반환되어 결과 조회에 쓰이므로, DB auto-increment가 아니라
 * <b>앱이 생성한 TSID</b>를 PK로 부여한다(order와 동일 전략 — {@link com.loopers.domain.AuditEntity} +
 * {@code Persistable}). 1인 1매는 {@code UNIQUE(user_id, coupon_id)}로 요청 단계에서 보장한다.
 */
public class CouponIssueRequestModel {

    private final Long id;
    private final Long userId;
    private final Long couponId;
    private CouponIssueRequestStatus status;
    private final ZonedDateTime requestedAt;
    private ZonedDateTime processedAt;   // 처리 확정 시각 (컨슈머가 채움). 미처리 시 null.

    public CouponIssueRequestModel(Long userId, Long couponId) {
        this.id = TsidGenerator.generate();
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponIssueRequestStatus.PENDING;
        this.requestedAt = ZonedDateTime.now();
        this.processedAt = null;
    }

    private CouponIssueRequestModel(Long id, Long userId, Long couponId, CouponIssueRequestStatus status,
                                    ZonedDateTime requestedAt, ZonedDateTime processedAt) {
        this.id = id;
        this.userId = userId;
        this.couponId = couponId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static CouponIssueRequestModel reconstitute(Long id, Long userId, Long couponId,
                                                       CouponIssueRequestStatus status,
                                                       ZonedDateTime requestedAt, ZonedDateTime processedAt) {
        return new CouponIssueRequestModel(id, userId, couponId, status, requestedAt, processedAt);
    }

    public boolean isPending() {
        return status == CouponIssueRequestStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public CouponIssueRequestStatus getStatus() {
        return status;
    }

    public ZonedDateTime getRequestedAt() {
        return requestedAt;
    }

    public ZonedDateTime getProcessedAt() {
        return processedAt;
    }
}
