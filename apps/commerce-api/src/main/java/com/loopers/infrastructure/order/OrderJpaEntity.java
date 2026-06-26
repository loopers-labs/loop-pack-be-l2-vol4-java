package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderSnapshot;
import com.loopers.domain.order.OrderStatus;
import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Column(name = "ref_user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Convert(converter = OrderSnapshotConverter.class)
    @Column(name = "snapshot", columnDefinition = "TEXT", nullable = false)
    private OrderSnapshot snapshot;

    protected OrderJpaEntity() {}

    @Override
    protected String idCode() {
        return "ORD";
    }

    OrderJpaEntity(String id, String userId, OrderStatus status, OrderSnapshot snapshot, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.userId = userId;
        this.status = status;
        this.snapshot = snapshot;
    }
}
