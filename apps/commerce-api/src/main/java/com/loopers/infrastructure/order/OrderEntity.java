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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    protected OrderEntity() {}

    public OrderEntity(Long userId, BigDecimal totalPrice) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalPrice = totalPrice;
    }

    public Order toDomain() {
        return new Order(getId(), userId, status, totalPrice,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(Order domain) {
        this.status = domain.getStatus();
    }
}
