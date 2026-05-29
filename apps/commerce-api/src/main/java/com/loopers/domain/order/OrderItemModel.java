package com.loopers.domain.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_items")
public class OrderItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Embedded
    private ProductSnapshot snapshot;

    @Column(nullable = false)
    private int quantity;

    public OrderItemModel(OrderModel order, Long productId, ProductSnapshot snapshot, int quantity) {
        this.order = order;
        this.productId = productId;
        this.snapshot = snapshot;
        this.quantity = quantity;
    }
}
