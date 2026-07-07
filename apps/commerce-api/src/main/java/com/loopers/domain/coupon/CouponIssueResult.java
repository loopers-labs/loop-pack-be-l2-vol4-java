package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "coupon_issue_result",
    uniqueConstraints = @UniqueConstraint(name = "uk_coupon_issue_result_request", columnNames = {"request_id"}))
public class CouponIssueResult extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private String requestId;
    @Column(name = "coupon_id", nullable = false)
    private Long couponId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueStatus status;
    @Column(name = "user_coupon_id")
    private Long userCouponId;
    @Column(name = "reason")
    private String reason;

    protected CouponIssueResult() {}

    public static CouponIssueResult pending(String requestId, Long couponId, Long userId) {
        CouponIssueResult r = new CouponIssueResult();
        r.requestId = requestId;
        r.couponId = couponId;
        r.userId = userId;
        r.status = CouponIssueStatus.PENDING;
        return r;
    }

    public String getRequestId() { return requestId; }
    public Long getCouponId() { return couponId; }
    public Long getUserId() { return userId; }
    public CouponIssueStatus getStatus() { return status; }
    public Long getUserCouponId() { return userCouponId; }
    public String getReason() { return reason; }
}
