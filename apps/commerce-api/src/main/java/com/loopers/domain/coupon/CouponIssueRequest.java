package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.UUID;

public class CouponIssueRequest {

    private final String requestId;
    private final Long couponId;
    private final String userLoginId;
    private IssueRequestStatus status;
    private IssueRejectReason rejectReason;
    private Long issuedCouponId;
    private final ZonedDateTime requestedAt;
    private ZonedDateTime processedAt;

    private CouponIssueRequest(
        String requestId,
        Long couponId,
        String userLoginId,
        IssueRequestStatus status,
        IssueRejectReason rejectReason,
        Long issuedCouponId,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
    ) {
        validateRequestId(requestId);
        validateCouponId(couponId);
        validateUserLoginId(userLoginId);
        validateStatus(status);
        validateRequestedAt(requestedAt);
        this.requestId = requestId;
        this.couponId = couponId;
        this.userLoginId = userLoginId;
        this.status = status;
        this.rejectReason = rejectReason;
        this.issuedCouponId = issuedCouponId;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    public static CouponIssueRequest request(Long couponId, String userLoginId, ZonedDateTime requestedAt) {
        return new CouponIssueRequest(
            UUID.randomUUID().toString(),
            couponId,
            userLoginId,
            IssueRequestStatus.REQUESTED,
            null,
            null,
            requestedAt,
            null
        );
    }

    public static CouponIssueRequest reconstruct(
        String requestId,
        Long couponId,
        String userLoginId,
        IssueRequestStatus status,
        IssueRejectReason rejectReason,
        Long issuedCouponId,
        ZonedDateTime requestedAt,
        ZonedDateTime processedAt
    ) {
        return new CouponIssueRequest(
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

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateCouponId(Long couponId) {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateUserLoginId(String userLoginId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 로그인 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateStatus(IssueRequestStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 상태는 비어있을 수 없습니다.");
        }
    }

    private void validateRequestedAt(ZonedDateTime requestedAt) {
        if (requestedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 시각은 비어있을 수 없습니다.");
        }
    }
}
