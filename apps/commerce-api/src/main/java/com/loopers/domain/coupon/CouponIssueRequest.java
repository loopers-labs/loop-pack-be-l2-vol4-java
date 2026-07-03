package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 선착순 쿠폰 발급 <b>요청</b> — 사용자가 폴링으로 결과를 확인할 수 있게 하는 추적 레코드.
 * <p>
 * requestId 는 UUID 로 사용자에게 반환되며 발급 결과 조회의 idempotency key 이자 캐시 키다.
 * consumer 는 requestId 기준으로 상태 전이시킨다.
 */
@Entity
@Table(name = "coupon_issue_requests", indexes = {
    @Index(name = "uk_cir_request_id", columnList = "request_id", unique = true)
})
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", nullable = false, length = 40, updatable = false)
    private String requestId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false, updatable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CouponIssueStatus status;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    protected CouponIssueRequest() {}

    public CouponIssueRequest(String requestId, Long userId, Long couponTemplateId) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "requestId 는 필수입니다.");
        }
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId 는 양수여야 합니다.");
        }
        if (couponTemplateId == null || couponTemplateId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponTemplateId 는 양수여야 합니다.");
        }
        this.requestId = requestId;
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = CouponIssueStatus.PENDING;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public CouponIssueStatus getStatus() {
        return status;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public ZonedDateTime getResolvedAt() {
        return resolvedAt;
    }

    /**
     * 발급 성공 — 이미 종료 상태면 멱등 통과.
     */
    public void markIssued(Long userCouponId, ZonedDateTime at) {
        if (this.status.isTerminal()) return;
        this.status = CouponIssueStatus.ISSUED;
        this.userCouponId = userCouponId;
        this.resolvedAt = at;
    }

    public void markRejected(CouponIssueStatus rejectStatus, String reason, ZonedDateTime at) {
        if (this.status.isTerminal()) return;
        if (rejectStatus == null || rejectStatus == CouponIssueStatus.PENDING || rejectStatus == CouponIssueStatus.ISSUED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "REJECTED_* 상태만 지정 가능합니다.");
        }
        this.status = rejectStatus;
        this.rejectReason = reason;
        this.resolvedAt = at;
    }
}
