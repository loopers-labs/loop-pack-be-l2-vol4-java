package com.loopers.domain.coupon;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class CouponIssueRequest extends DomainEntity {

    private Long couponTemplateId;
    private String userId;
    private CouponIssueRequestStatus status;
    private Long issuedCouponId;
    private String failureReason;
    private ZonedDateTime completedAt;

    public CouponIssueRequest(Long couponTemplateId, String userId) {
        validateCouponTemplateId(couponTemplateId);
        validateUserId(userId);

        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public static CouponIssueRequest reconstruct(
        Long id,
        Long couponTemplateId,
        String userId,
        CouponIssueRequestStatus status,
        Long issuedCouponId,
        String failureReason,
        ZonedDateTime completedAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        CouponIssueRequest request = new CouponIssueRequest(couponTemplateId, userId);
        request.status = status == null ? CouponIssueRequestStatus.PENDING : status;
        request.issuedCouponId = issuedCouponId;
        request.failureReason = failureReason;
        request.completedAt = completedAt;
        request.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return request;
    }

    public boolean isPending() {
        return status == CouponIssueRequestStatus.PENDING;
    }

    public void succeed(Long issuedCouponId) {
        if (!isPending()) {
            return;
        }
        if (issuedCouponId == null || issuedCouponId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 쿠폰 ID는 필수입니다.");
        }

        this.status = CouponIssueRequestStatus.SUCCEEDED;
        this.issuedCouponId = issuedCouponId;
        this.failureReason = null;
        this.completedAt = ZonedDateTime.now();
    }

    public void fail(String reason) {
        if (!isPending()) {
            return;
        }

        this.status = CouponIssueRequestStatus.FAILED;
        this.failureReason = reason == null || reason.isBlank() ? "쿠폰 발급에 실패했습니다." : reason;
        this.completedAt = ZonedDateTime.now();
    }

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public String getUserId() {
        return userId;
    }

    public CouponIssueRequestStatus getStatus() {
        return status;
    }

    public Long getIssuedCouponId() {
        return issuedCouponId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    private void validateCouponTemplateId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
    }

    private void validateUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }
}
