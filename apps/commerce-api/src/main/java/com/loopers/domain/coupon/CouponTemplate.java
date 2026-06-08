package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.policy.CouponDiscountPolicy;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.domain.coupon.vo.CouponExpiration;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.CouponName;
import com.loopers.domain.coupon.vo.DiscountValue;
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
    private DiscountValue discountValue;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "min_order_amount"))
    private CouponMoney minimumOrderAmount;

    @Embedded
    @AttributeOverride(name = "expiredAt", column = @Column(name = "expired_at", nullable = false))
    private CouponExpiration expiration;

    private CouponTemplate(
        CouponName name,
        CouponType type,
        DiscountValue discountValue,
        CouponMoney minimumOrderAmount,
        CouponExpiration expiration
    ) {
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minimumOrderAmount = minimumOrderAmount;
        this.expiration = expiration;
    }

    public static CouponTemplate create(
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt,
        CouponDiscountPolicy policy
    ) {
        confirmCanCreate(type, policy);
        return new CouponTemplate(
            CouponName.of(name),
            type,
            createDiscountValue(discountValue, policy),
            createMinimumOrderAmount(minimumOrderAmount),
            CouponExpiration.of(expiredAt)
        );
    }

    public void update(
        String name,
        CouponType type,
        long discountValue,
        Long minimumOrderAmount,
        ZonedDateTime expiredAt,
        CouponDiscountPolicy policy
    ) {
        confirmCanCreate(type, policy);
        this.name = CouponName.of(name);
        this.type = type;
        this.discountValue = createDiscountValue(discountValue, policy);
        this.minimumOrderAmount = createMinimumOrderAmount(minimumOrderAmount);
        this.expiration = CouponExpiration.of(expiredAt);
    }

    public CouponDiscount apply(CouponMoney orderAmount, ZonedDateTime now, CouponDiscountPolicy policy) {
        confirmDiscountPolicy(type, policy);
        confirmCanApplyToOrder(orderAmount, now);

        CouponMoney discountAmount = policy.discount(orderAmount, discountValue);
        return CouponDiscount.of(orderAmount, discountAmount);
    }

    public UserCoupon issue(Long userId, ZonedDateTime issuedAt) {
        confirmCanIssue(issuedAt);
        return UserCoupon.issue(userId, getId(), this);
    }

    public String getName() {
        return name.value();
    }

    private boolean isExpiredAt(ZonedDateTime now) {
        return expiration.isExpiredAt(now);
    }

    private void confirmCanApplyToOrder(CouponMoney orderAmount, ZonedDateTime now) {
        confirmOrderAmount(orderAmount);
        confirmNotExpired(now);
        confirmMinimumOrderAmount(orderAmount);
    }

    private static void confirmCanCreate(CouponType type, CouponDiscountPolicy policy) {
        confirmCouponType(type);
        confirmDiscountPolicy(type, policy);
    }

    private static DiscountValue createDiscountValue(long discountValue, CouponDiscountPolicy policy) {
        DiscountValue discount = DiscountValue.of(discountValue);
        policy.confirmDiscountValue(discount);
        return discount;
    }

    private static CouponMoney createMinimumOrderAmount(Long minimumOrderAmount) {
        return minimumOrderAmount == null ? null : CouponMoney.of(minimumOrderAmount);
    }

    private static void confirmCouponType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
    }

    private static void confirmDiscountPolicy(CouponType type, CouponDiscountPolicy policy) {
        if (policy == null || policy.type() != type) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 타입에 맞는 할인 정책이 없습니다.");
        }
    }

    private static void confirmOrderAmount(CouponMoney orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 비어있을 수 없습니다.");
        }
    }

    private void confirmCanIssue(ZonedDateTime issuedAt) {
        confirmNotExpired(issuedAt);
    }

    private void confirmNotExpired(ZonedDateTime now) {
        if (isExpiredAt(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
    }

    private void confirmMinimumOrderAmount(CouponMoney orderAmount) {
        if (!meetsMinimumOrderAmount(orderAmount)) {
            throw new CoreException(ErrorType.CONFLICT, "최소 주문 금액을 충족하지 못했습니다.");
        }
    }

    private boolean meetsMinimumOrderAmount(CouponMoney orderAmount) {
        return minimumOrderAmount == null || !orderAmount.isLessThan(minimumOrderAmount);
    }
}
