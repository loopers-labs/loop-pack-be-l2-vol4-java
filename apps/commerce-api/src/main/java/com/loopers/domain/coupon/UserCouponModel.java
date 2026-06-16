package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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
@Builder
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    public UserCouponStatus getStatus(ZonedDateTime now) {
        if (usedAt != null) {
            return UserCouponStatus.USED;
        }

        if (expiredAt.isBefore(now)) {
            return UserCouponStatus.EXPIRED;
        }

        return UserCouponStatus.AVAILABLE;
    }

    public int apply(int orderAmount, ZonedDateTime now) {
        if (getStatus(now) != UserCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용했거나 만료된 쿠폰입니다.");
        }

        if (orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.CONFLICT, String.format("주문 금액이 쿠폰의 최소 주문 금액(%d원)에 미치지 못합니다.", minOrderAmount));
        }

        int discountAmount = discountType.calculate(orderAmount, discountValue);
        this.usedAt = now;

        return discountAmount;
    }
}
