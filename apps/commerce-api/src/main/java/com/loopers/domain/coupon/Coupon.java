package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 템플릿(정책). 발급의 원본이 된다.
 * 할인 정책(type+value)은 Discount VO 로, 최소 주문 금액은 Money VO 로 검증한다.
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Embedded
    private Discount discount;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "min_order_amount"))
    private Money minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    public Coupon(String name, Discount discount, Money minOrderAmount, LocalDateTime expiredAt) {
        validate(name, expiredAt);
        this.name = name;
        this.discount = discount;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private static void validate(String name, LocalDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 비어있을 수 없습니다.");
        }
    }

    public void update(String name, Discount discount, Money minOrderAmount, LocalDateTime expiredAt) {
        validate(name, expiredAt);
        this.name = name;
        this.discount = discount;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public Money discountFor(Money orderAmount) {
        if (minOrderAmount != null && orderAmount.isLessThan(minOrderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }
        return discount.apply(orderAmount);
    }
}
