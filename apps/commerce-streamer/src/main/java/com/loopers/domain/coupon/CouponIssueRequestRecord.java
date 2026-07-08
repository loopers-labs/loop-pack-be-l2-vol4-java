package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

public class CouponIssueRequestRecord {

    private final String requestId;
    private final Long couponId;
    private final String userLoginId;
    private IssueRequestStatus status;
    private IssueRejectReason rejectReason;
    private Long issuedCouponId;
    private final ZonedDateTime requestedAt;
    private ZonedDateTime processedAt;

    private CouponIssueRequestRecord(
        String requestId,
        Long couponId,
        String userLoginId,
        IssueRequestStatus status,
        IssueRejectReason rejectReason,
        Long issuedCouponId,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
    ) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userLoginId = userLoginId;
        this.status = status;
        this.rejectReason = rejectReason;
        this.issuedCouponId = issuedCouponId;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    public static CouponIssueRequestRecord restore(
        String requestId,
        Long couponId,
        String userLoginId,
        IssueRequestStatus status,
        IssueRejectReason rejectReason,
        Long issuedCouponId,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
    ) {
        return new CouponIssueRequestRecord(
            requestId,
            couponId,
            userLoginId,
            status,
            rejectReason,
            issuedCouponId,
            requestedAt,
            processedAt
        );
    }

    public void issue(Long issuedCouponId, ZonedDateTime processedAt) {
        this.status = IssueRequestStatus.ISSUED;
        this.rejectReason = null;
        this.issuedCouponId = issuedCouponId;
        this.processedAt = processedAt;
    }

    public void reject(IssueRejectReason reason, ZonedDateTime processedAt) {
        this.status = IssueRequestStatus.REJECTED;
        this.rejectReason = reason;
        this.issuedCouponId = null;
        this.processedAt = processedAt;
    }

    public boolean isRequested() {
        return status == IssueRequestStatus.REQUESTED;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public IssueRequestStatus getStatus() {
        return status;
    }

    public IssueRejectReason getRejectReason() {
        return rejectReason;
    }

    public Long getIssuedCouponId() {
        return issuedCouponId;
    }

    public ZonedDateTime getRequestedAt() {
        return requestedAt;
    }

    public ZonedDateTime getProcessedAt() {
        return processedAt;
    }
}
