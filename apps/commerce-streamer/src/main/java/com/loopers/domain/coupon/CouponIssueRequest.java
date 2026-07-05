package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * streamer 가 처리 결과를 되돌려 기록하는 coupon_issue_request 매핑.
 * api 가 PENDING 으로 만든 행을 streamer 가 findById 로 찾아 ISSUED/REJECTED 로 확정한다(상태 갱신 전용).
 */
@Entity
@Table(name = "coupon_issue_request")
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_policy_id", nullable = false)
    private Long couponPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueRequestStatus status;

    protected CouponIssueRequest() {}

    private CouponIssueRequest(Long userId, Long couponPolicyId) {
        this.userId = userId;
        this.couponPolicyId = couponPolicyId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public static CouponIssueRequest pending(Long userId, Long couponPolicyId) {
        return new CouponIssueRequest(userId, couponPolicyId);
    }

    public void markIssued() {
        this.status = CouponIssueRequestStatus.ISSUED;
    }

    public void markRejected() {
        this.status = CouponIssueRequestStatus.REJECTED;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponPolicyId() {
        return couponPolicyId;
    }

    public CouponIssueRequestStatus getStatus() {
        return status;
    }
}
