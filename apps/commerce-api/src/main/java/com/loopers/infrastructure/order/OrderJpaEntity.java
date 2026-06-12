package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
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

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Long originalAmount;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long finalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLineJpaEntity> orderLines = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    private OrderJpaEntity(
        String userLoginId,
        OrderStatus status,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        List<OrderLineJpaEntity> orderLines
    ) {
        this.userLoginId = userLoginId;
        this.status = status;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.orderLines.addAll(orderLines);
    }

    public static OrderJpaEntity from(Order order) {
        return new OrderJpaEntity(
            order.getUserLoginId(),
            order.getStatus(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            order.getOrderLines().stream()
                .map(OrderLineJpaEntity::from)
                .toList()
        );
    }

    public Order toDomain() {
        return Order.reconstruct(
            getId(),
            userLoginId,
            status,
            orderLines.stream()
                .map(OrderLineJpaEntity::toDomain)
                .toList(),
            discountAmount,
            getCreatedAt()
        );
    }

    public void update(Order order) {
        this.userLoginId = order.getUserLoginId();
        this.status = order.getStatus();
        this.originalAmount = order.getOriginalAmount();
        this.discountAmount = order.getDiscountAmount();
        this.finalAmount = order.getFinalAmount();
        this.orderLines.clear();
        this.orderLines.addAll(order.getOrderLines().stream()
            .map(OrderLineJpaEntity::from)
            .toList());
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getOriginalAmount() {
        return originalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return finalAmount;
    }

    public List<OrderLineJpaEntity> getOrderLines() {
        return orderLines;
    }
}
