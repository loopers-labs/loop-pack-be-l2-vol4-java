package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 할인 정책 — 할인 종류(type)·값(value)과 사용 조건(minOrderAmount)을 묶고 할인 계산 규칙을 캡슐화한 불변 값 객체.
 * 같은 VO 를 {@code CouponTemplate}(원형 정의)과 {@code UserCoupon}(발급 스냅샷)이 각각 보유한다.
 * {@code minOrderAmount} 는 이 할인이 적용 가능한 최소 주문 금액으로, {@code 0} 이면 최소 금액 제한이 없다.
 */
@Getter
@EqualsAndHashCode
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountPolicy {

    private static final long RATE_MAX = 100L;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType type;

    @Column(name = "discount_value", nullable = false)
    private long value;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    private DiscountPolicy(DiscountType type, long value, long minOrderAmount) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 종류는 비어있을 수 없습니다.");
        }
        if (value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 1 이상이어야 합니다.");
        }
        if (type == DiscountType.RATE && value > RATE_MAX) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인 값은 1~100 사이여야 합니다.");
        }
        if (minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
    }

    /** 최소 주문 금액 제한이 없는 정책. */
    public static DiscountPolicy of(DiscountType type, long value) {
        return new DiscountPolicy(type, value, 0L);
    }

    public static DiscountPolicy of(DiscountType type, long value, long minOrderAmount) {
        return new DiscountPolicy(type, value, minOrderAmount);
    }

    /**
     * 적용 전 금액에 정책을 적용한 할인액을 계산한다.
     * 적용 전 금액이 최소 주문 금액에 미달하면 이 쿠폰은 사용할 수 없으므로 거부한다.
     * 할인액은 0 이상이며 적용 전 금액을 초과하지 않는다.
     */
    public Money calculate(Money orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "적용 전 금액은 비어있을 수 없습니다.");
        }
        if (orderAmount.getAmount() < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "최소 주문 금액 " + minOrderAmount + "원 이상부터 사용할 수 있는 쿠폰입니다.");
        }
        try {
            return Money.of(type.discountOf(orderAmount.getAmount(), value));
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인액 계산 중 오버플로우가 발생했습니다.");
        }
    }
}
