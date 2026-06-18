package com.loopers.domain.coupon;

/**
 * 주문에 적용된 쿠폰 결과 (UC-17). 사용 처리된 발급분 ID와 산출된 할인 금액.
 * OrderService가 주문 스냅샷(userCouponId/discountAmount)에 반영한다.
 */
public record AppliedCoupon(Long userCouponId, long discountAmount) {
}
