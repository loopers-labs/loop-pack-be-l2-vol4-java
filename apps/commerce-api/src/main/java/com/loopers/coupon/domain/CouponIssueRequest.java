package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선착순 쿠폰 발급 "요청"의 상태. API 는 PENDING 으로 적재만 하고 즉시 응답하며,
 * Consumer 가 실제 발급/거절을 판정해 SUCCESS/REJECTED 로 갱신한다. 사용자는 requestId 로 결과를 polling 한다.
 */
@Entity
@Table(name = "coupon_issue_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon_issue_request_id", columnNames = "request_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueRequestStatus status;

    @Column(name = "reason")
    private String reason;

    private CouponIssueRequest(String requestId, Long couponId, Long userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public static CouponIssueRequest pending(String requestId, Long couponId, Long userId) {
        return new CouponIssueRequest(requestId, couponId, userId);
    }

    public void markSuccess() {
        this.status = CouponIssueRequestStatus.SUCCESS;
        this.reason = null;
    }

    public void markRejected(String reason) {
        this.status = CouponIssueRequestStatus.REJECTED;
        this.reason = reason;
    }
}
