package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "type", column = @Column(name = "discount_type", nullable = false)),
            @AttributeOverride(name = "value", column = @Column(name = "discount_value", nullable = false))
    })
    private DiscountPolicy discountPolicy;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "quantity")
    private Integer quantity;

    private CouponModel(String name, DiscountPolicy discountPolicy, Long minOrderAmount, LocalDateTime expiredAt, Integer quantity) {
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.quantity = quantity;
    }

    public static CouponModel of(String name, DiscountPolicy discountPolicy, Long minOrderAmount, LocalDateTime expiredAt, Integer quantity) {
        return new CouponModel(name, discountPolicy, minOrderAmount, expiredAt, quantity);
    }

    public void update(String name, DiscountPolicy discountPolicy, Long minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.discountPolicy = discountPolicy;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void decreaseQuantity() {
        if (quantity == null) {  // 수량이 없는 무제한 쿠폰
            return;
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
        }
        this.quantity -= 1;
    }

    public long calculateDiscount(long orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않아 쿠폰을 사용할 수 없습니다.");
        }
        return discountPolicy.discountFor(orderAmount);
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiredAt);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CouponModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return CouponModel.class.hashCode();
    }
}
