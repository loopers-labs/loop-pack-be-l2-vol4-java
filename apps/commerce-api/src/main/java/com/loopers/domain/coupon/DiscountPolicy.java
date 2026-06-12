package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class DiscountPolicy {

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType type;

    @Column(name = "discount_value", nullable = false)
    private long value;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    protected DiscountPolicy() {}

    private DiscountPolicy(DiscountType type, long value, Money minOrderAmount) {
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
    }

    public static DiscountPolicy of(DiscountType type, long value, Money minOrderAmount) {
        return new DiscountPolicy(type, value, minOrderAmount);
    }

    public Money calculateDiscount(Money orderAmount) {
        if (!orderAmount.isGreaterThanOrEqual(minOrderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "최소 주문 금액(" + minOrderAmount.amount() + "원) 미만입니다.");
        }
        return type.discount(value, orderAmount);
    }

    public DiscountType getType() { return type; }
    public long getValue() { return value; }
    public Money getMinOrderAmount() { return minOrderAmount; }
}
