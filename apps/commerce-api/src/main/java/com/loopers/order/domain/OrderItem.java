package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderModel order;

    @Embedded private OrderItemSnapshot snapshot;

    private int quantity;
    private Long lineAmount;

    protected OrderItem() {}

    OrderItem(OrderModel order, OrderItemSnapshot snapshot, int quantity) {
        this.order = order;
        this.snapshot = snapshot;
        this.quantity = quantity;
        this.lineAmount = snapshot.getUnitPrice() * quantity;
    }
}
