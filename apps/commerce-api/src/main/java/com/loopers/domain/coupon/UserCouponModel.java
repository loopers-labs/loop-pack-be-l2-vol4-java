package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_coupons_user_id_coupon_id", columnNames = {"user_id", "coupon_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Builder
    private UserCouponModel(
        Long userId,
        Long couponId,
        String name,
        DiscountType discountType,
        int discountValue,
        int minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        this.userId = userId;
        this.couponId = couponId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static UserCouponModel issue(Long userId, CouponModel coupon) {
        return UserCouponModel.builder()
            .userId(userId)
            .couponId(coupon.getId())
            .name(coupon.getName().value())
            .discountType(coupon.getType())
            .discountValue(coupon.getDiscountValue())
            .minOrderAmount(coupon.getMinOrderAmount().value())
            .expiredAt(coupon.getExpiredAt().value())
            .build();
    }
}
