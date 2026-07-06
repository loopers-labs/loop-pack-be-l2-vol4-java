package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * commerce-api 가 적재한 발급 요청. Consumer 가 발급/거절 판정 후 상태를 갱신한다(사용자는 polling 으로 조회).
 */
@Entity
@Table(name = "coupon_issue_request")
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
