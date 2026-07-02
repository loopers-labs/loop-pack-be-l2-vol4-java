package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선착순 쿠폰 발급 요청 한 건 — "신청했다"는 원천 사실이자, 유저가 결과를 폴링하는 대상.
 * API 는 이 요청을 저장·발행만 하고 즉시 응답하며, 실제 발급은 Consumer 가 처리 후 상태를 갱신한다.
 * 상태가 PENDING 일 때만 처리되므로 requestId 가 곧 Consumer 의 멱등 키가 된다.
 */
@Entity
@Table(name = "coupon_issue_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest extends BaseEntity {

    // 유저에게 즉시 돌려주는 폴링용 식별자(UUID)
    @Column(nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(length = 200)
    private String reason;

    // 발급 성공 시 생성된 UserCoupon 참조 — 폴링 응답으로 전달
    @Column
    private Long issuedUserCouponId;

    public CouponIssueRequest(String requestId, Long couponId, Long userId) {
        validate(requestId, couponId, userId);
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    private static void validate(String requestId, Long couponId, Long userId) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 요청에는 requestId 가 필요합니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 요청에는 couponId 가 필요합니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 요청에는 userId 가 필요합니다.");
        }
    }

    public boolean isPending() {
        return status == CouponIssueRequestStatus.PENDING;
    }

    public void markIssued(Long userCouponId) {
        this.status = CouponIssueRequestStatus.ISSUED;
        this.issuedUserCouponId = userCouponId;
    }

    public void reject(String reason) {
        this.status = CouponIssueRequestStatus.REJECTED;
        this.reason = reason;
    }
}
