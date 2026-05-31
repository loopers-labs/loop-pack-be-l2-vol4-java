package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.policy.CouponDiscountPolicy;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.domain.coupon.vo.CouponExpiration;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.CouponName;
import com.loopers.domain.coupon.vo.CouponValue;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupon_template")
public class CouponTemplate extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private CouponName name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "value", nullable = false))
    private CouponValue couponValue;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "min_order_amount"))
    private CouponMoney minOrderAmount;

    @Embedded
    @AttributeOverride(name = "expiredAt", column = @Column(name = "expired_at", nullable = false))
    private CouponExpiration expiration;

    private CouponTemplate(
        CouponName name,
        CouponType type,
        CouponValue couponValue,
        CouponMoney minOrderAmount,
        CouponExpiration expiration
    ) {
        this.name = name;
        this.type = type;
        this.couponValue = couponValue;
        this.minOrderAmount = minOrderAmount;
        this.expiration = expiration;
    }

    public static CouponTemplate create(
        String name,
        CouponType type,
        long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponDiscountPolicy policy
    ) {
        validateType(type);
        validatePolicy(type, policy);
        CouponName couponName = CouponName.of(name);
        CouponValue couponValue = CouponValue.of(value);
        policy.validateValue(couponValue);
        CouponMoney minimum = minOrderAmount == null ? null : CouponMoney.of(minOrderAmount);
        CouponExpiration expiration = CouponExpiration.of(expiredAt);
        return new CouponTemplate(couponName, type, couponValue, minimum, expiration);
    }

    public CouponDiscount apply(CouponMoney orderAmount, ZonedDateTime now, CouponDiscountPolicy policy) {
        validateOrderAmount(orderAmount);
        validatePolicy(type, policy);
        validateNotExpired(now);
        validateMinimumOrderAmount(orderAmount);

        CouponMoney discountAmount = policy.calculateDiscount(orderAmount, couponValue);
        return CouponDiscount.of(orderAmount, discountAmount);
    }

    public String getName() {
        return name.value();
    }

    public boolean isExpiredAt(ZonedDateTime now) {
        return expiration.isExpiredAt(now);
    }

    private static void validateType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
    }

    private static void validatePolicy(CouponType type, CouponDiscountPolicy policy) {
        if (policy == null || policy.type() != type) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 타입에 맞는 할인 정책이 없습니다.");
        }
    }

    private static void validateOrderAmount(CouponMoney orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 비어있을 수 없습니다.");
        }
    }

    private void validateNotExpired(ZonedDateTime now) {
        if (isExpiredAt(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
    }

    private void validateMinimumOrderAmount(CouponMoney orderAmount) {
        if (minOrderAmount != null && orderAmount.isLessThan(minOrderAmount)) {
            throw new CoreException(ErrorType.CONFLICT, "최소 주문 금액을 충족하지 못했습니다.");
        }
    }
}
