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
    private OrderStatus status;
    private BigDecimal totalPrice;

    public Order(Long userId, BigDecimal totalPrice) {
        validate(userId, totalPrice);
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalPrice = totalPrice;
    }

    public Order(Long id, Long userId, OrderStatus status, BigDecimal totalPrice,
                 ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
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

    private void validate(Long userId, BigDecimal totalPrice) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 주문 금액은 0 이상이어야 합니다.");
        }
    }
}
