package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class OrderJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @BatchSize(size = 100)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    private OrderJpaEntity(Long userId, OrderStatus status, long totalAmount, List<OrderItemJpaEntity> items) {
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    public static OrderJpaEntity of(Long userId,
                                    OrderStatus status,
                                    long totalAmount,
                                    List<OrderItemJpaEntity> items) {
        return new OrderJpaEntity(userId, status, totalAmount, items);
    }
}
