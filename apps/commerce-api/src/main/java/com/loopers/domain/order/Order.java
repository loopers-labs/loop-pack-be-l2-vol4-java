package com.loopers.domain.order;

import com.loopers.domain.BaseDomain;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
public class Order extends BaseDomain {

    private Long userId;
    private Long issuedCouponId;
    private OrderStatus status;
    private BigDecimal originalPrice;
    private BigDecimal discountAmount;
    private BigDecimal totalPrice;

    public Order(Long userId, Long issuedCouponId, BigDecimal originalPrice, BigDecimal discountAmount) {
        validate(userId, originalPrice, discountAmount);
        this.userId = userId;
        this.issuedCouponId = issuedCouponId;
        this.status = OrderStatus.PENDING;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.totalPrice = originalPrice.subtract(discountAmount);
    }

    public Order(Long id, Long userId, Long issuedCouponId, OrderStatus status, BigDecimal originalPrice,
                 BigDecimal discountAmount, BigDecimal totalPrice, ZonedDateTime createdAt, ZonedDateTime updatedAt,
                 ZonedDateTime deletedAt) {
        this.id = id;
        this.userId = userId;
        this.issuedCouponId = issuedCouponId;
        this.status = status;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELED;
    }

    private void validate(Long userId, BigDecimal originalPrice, BigDecimal discountAmount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        if (discountAmount.compareTo(originalPrice) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.");
        }
    }
}
