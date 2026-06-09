package com.loopers.coupon.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {
    COUPON_NOT_FOUND("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_ISSUED("COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다."),
    COUPON_ALREADY_USED("COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
    COUPON_EXPIRED("COUPON_EXPIRED", "만료된 쿠폰입니다."),
    COUPON_NOT_OWNED("COUPON_NOT_OWNED", "본인 소유의 쿠폰이 아닙니다."),
    COUPON_MIN_ORDER_NOT_MET("COUPON_MIN_ORDER_NOT_MET", "최소 주문 금액을 충족하지 못했습니다."),
    INVALID_COUPON_VALUE("INVALID_COUPON_VALUE", "쿠폰 할인 값이 올바르지 않습니다.");

    private final String code;
    private final String message;
}
