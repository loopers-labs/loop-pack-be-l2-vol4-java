package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class OrderEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineEntity> orderLines = new ArrayList<>();

    private Long originalTotalPrice;
    private Long discountPrice;
    private Long totalPrice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private OrderEntity(Long userId, Long originalTotalPrice, Long discountPrice, Long totalPrice, OrderStatus status) {
        this.userId = userId;
        this.originalTotalPrice = originalTotalPrice;
        this.discountPrice = discountPrice;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public static OrderEntity from(OrderModel model) {
        OrderEntity entity = new OrderEntity(
            model.getUserId(),
            model.getOriginalTotalPrice(),
            model.getDiscountPrice(),
            model.getTotalPrice(),
            model.getStatus()
        );
        model.getOrderLines().stream()
            .map(line -> OrderLineEntity.from(line, entity))
            .forEach(entity.orderLines::add);
        return entity;
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderModel toDomain() {
        return new OrderModel(
            getId(),
            userId,
            orderLines.stream().map(OrderLineEntity::toDomain).toList(),
            originalTotalPrice,
            discountPrice,
            totalPrice,
            status,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
