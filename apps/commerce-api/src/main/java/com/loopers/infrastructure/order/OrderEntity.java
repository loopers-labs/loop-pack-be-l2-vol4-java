package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Entity(name = "Order")
@Table(name = "orders")
public class OrderEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    protected OrderEntity() {}

    public OrderEntity(Long userId, Long issuedCouponId, BigDecimal originalPrice, BigDecimal discountAmount) {
        this.userId = userId;
        this.issuedCouponId = issuedCouponId;
        this.status = OrderStatus.PENDING;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.totalPrice = originalPrice.subtract(discountAmount);
    }

    public Order toDomain() {
        return new Order(getId(), userId, issuedCouponId, status, originalPrice, discountAmount, totalPrice,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(Order domain) {
        this.status = domain.getStatus();
    }
}
