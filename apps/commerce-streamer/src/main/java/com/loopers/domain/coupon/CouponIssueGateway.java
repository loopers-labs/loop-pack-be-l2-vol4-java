package com.loopers.domain.coupon;

/**
 * commerce-api 소유 테이블(coupons/user_coupons/coupon_issue_request)에 대한 최소 개입 포트.
 * streamer 는 이 테이블들을 자기 도메인으로 이중 소유하지 않고, 발급에 필요한 연산만 SQL 로 수행한다(A2).
 */
public interface CouponIssueGateway {

    boolean alreadyIssued(Long userId, Long couponId);

    /** 수량을 원자적으로 1 차감한다. 남은 수량이 있어 차감됐으면 true, 소진이면 false. */
    boolean decreaseQuantity(Long couponId);

    void insertUserCoupon(Long userId, Long couponId);

    void markIssued(String requestId);

    void markRejected(String requestId, String reason);
}