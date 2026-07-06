package com.loopers.application.coupon;

public interface CouponIssueRedisStore {

    ReservationResult reserve(Long couponId, Long userId, Integer totalQuantity, String requestId);

    void confirmIssue(Long couponId, Long userId, String requestId);

    void cancelReservation(Long couponId, Long userId, String requestId);

    enum ReservationResult {
        RESERVED,
        DUPLICATE,
        SOLD_OUT
    }
}
