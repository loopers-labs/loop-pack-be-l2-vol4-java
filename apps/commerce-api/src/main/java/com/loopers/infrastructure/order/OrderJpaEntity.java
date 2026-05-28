package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderStatus;
import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "orders")
@Getter
public class OrderJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    protected OrderJpaEntity() {}

    OrderJpaEntity(Long id, Long userId, OrderStatus status, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.userId = userId;
        this.status = status;
    }
}
