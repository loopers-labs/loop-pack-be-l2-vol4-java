package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 선착순 쿠폰 발급 "요청" 애그리거트. 실제 발급물(UserCoupon)과 분리된, 요청의 수명주기를 추적한다.
 * API 가 PENDING 으로 접수해 저장하고, 컨슈머(streamer)가 처리 결과에 따라 ISSUED/REJECTED 로 확정한다.
 * 유저는 이 요청 id 로 처리 상태를 폴링한다.
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
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 비어있을 수 없습니다.");
        }
        if (couponPolicyId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 정책 ID 는 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.couponPolicyId = couponPolicyId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public static CouponIssueRequest pending(Long userId, Long couponPolicyId) {
        return new CouponIssueRequest(userId, couponPolicyId);
    }

    /** 발급 성공으로 확정한다. */
    public void markIssued() {
        changeStatusTo(CouponIssueRequestStatus.ISSUED);
    }

    /** 수량 소진 등으로 발급 거절을 확정한다. */
    public void markRejected() {
        changeStatusTo(CouponIssueRequestStatus.REJECTED);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    private void changeStatusTo(CouponIssueRequestStatus next) {
        if (status != CouponIssueRequestStatus.PENDING) {
            throw new CoreException(ErrorType.COUPON_ISSUE_REQUEST_ALREADY_HANDLED, "");
        }
        this.status = next;
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
