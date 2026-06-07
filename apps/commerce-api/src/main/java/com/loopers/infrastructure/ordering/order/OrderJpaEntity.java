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

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_amount", nullable = false)
    private Long finalAmount;

    @Column(name = "coupon_id")
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    protected OrderJpaEntity() {}

    private OrderJpaEntity(
        String userId,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long couponId,
        OrderStatus status,
        List<OrderLineJpaEntity> lines
    ) {
        this.userId = userId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.couponId = couponId;
        this.status = status;
        this.lines = new ArrayList<>(lines);
    }

    public static OrderJpaEntity from(Order order) {
        return new OrderJpaEntity(
            order.getUserId(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            order.getCouponId(),
            order.getStatus(),
            order.getLines().stream().map(OrderLineJpaEntity::from).toList()
        );
    }

    public Order toDomain() {
        return Order.reconstruct(
            getId(),
            userId,
            originalAmount,
            discountAmount,
            finalAmount,
            couponId,
            status,
            lines.stream().map(OrderLineJpaEntity::toDomain).toList(),
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(Order order) {
        this.userId = order.getUserId();
        this.originalAmount = order.getOriginalAmount();
        this.discountAmount = order.getDiscountAmount();
        this.finalAmount = order.getFinalAmount();
        this.couponId = order.getCouponId();
        this.status = order.getStatus();
    }
}
