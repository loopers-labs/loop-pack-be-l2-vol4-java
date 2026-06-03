package com.loopers.infrastructure.ordering.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    protected OrderJpaEntity() {}

    private OrderJpaEntity(String userId, Long totalAmount, OrderStatus status, List<OrderLineJpaEntity> lines) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.lines = new ArrayList<>(lines);
    }

    public static OrderJpaEntity from(Order order) {
        return new OrderJpaEntity(
            order.getUserId(),
            order.getTotalAmount(),
            order.getStatus(),
            order.getLines().stream().map(OrderLineJpaEntity::from).toList()
        );
    }

    public Order toDomain() {
        return Order.reconstruct(
            getId(),
            userId,
            totalAmount,
            status,
            lines.stream().map(OrderLineJpaEntity::toDomain).toList(),
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(Order order) {
        this.userId = order.getUserId();
        this.totalAmount = order.getTotalAmount();
        this.status = order.getStatus();
    }
}
