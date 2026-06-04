package com.loopers.domain.order.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class OrderPayment {

    @Column(name = "order_total_price", nullable = false)
    private long orderAmount;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    private OrderPayment(long orderAmount, long discountAmount, long paymentAmount) {
        if (orderAmount < 0 || discountAmount < 0 || paymentAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
        if (orderAmount != discountAmount + paymentAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 결제 금액 계산 결과가 올바르지 않습니다.");
        }

        this.orderAmount = orderAmount;
        this.discountAmount = discountAmount;
        this.paymentAmount = paymentAmount;
    }

    public static OrderPayment withoutDiscount(long orderAmount) {
        return new OrderPayment(orderAmount, 0L, orderAmount);
    }

    public static OrderPayment withDiscount(long orderAmount, long discountAmount) {
        return new OrderPayment(orderAmount, discountAmount, orderAmount - discountAmount);
    }

    public long orderAmount() {
        return orderAmount;
    }

    public long discountAmount() {
        return discountAmount;
    }

    public long paymentAmount() {
        return paymentAmount;
    }

    public boolean hasDiscount() {
        return discountAmount > 0;
    }
}
