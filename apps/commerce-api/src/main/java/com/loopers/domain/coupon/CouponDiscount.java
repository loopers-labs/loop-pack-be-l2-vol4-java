package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponDiscount {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column
    private Long minOrderAmount;

    public CouponDiscount(CouponType type, Long value, Long minOrderAmount) {
        Guard.notNull(type, "쿠폰 타입은 필수입니다.");
        Guard.notNull(value, "쿠폰 할인 값은 필수입니다.");
        type.validate(value, minOrderAmount);
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
    }

    public long calculateDiscount(long orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 조건을 충족하지 못했습니다.");
        }
        long discount = type.calculate(orderAmount, value);
        return Math.min(discount, orderAmount);
    }
}
