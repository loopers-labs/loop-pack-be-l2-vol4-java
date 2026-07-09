package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Kafka 기반 비동기 쿠폰 발급 요청의 처리 상태를 추적한다.
 * API는 이 레코드를 PENDING으로 생성하고 발급 요청만 발행하며, 실제 발급은 Consumer가 처리 후 ISSUED/FAILED로 갱신한다.
 * 상태 자체가 멱등 가드 역할을 한다 — 이미 PENDING이 아니면(ISSUED/FAILED) Consumer는 재처리하지 않는다.
 */
@Getter
@Entity
@Table(name = "coupon_issue_requests")
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false, updatable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CouponIssueRequestStatus status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    protected CouponIssueRequestModel() {}

    public CouponIssueRequestModel(Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public void markIssued(Long userCouponId) {
        this.status = CouponIssueRequestStatus.ISSUED;
        this.userCouponId = userCouponId;
    }

    public void markFailed(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.failureReason = reason;
    }
}
