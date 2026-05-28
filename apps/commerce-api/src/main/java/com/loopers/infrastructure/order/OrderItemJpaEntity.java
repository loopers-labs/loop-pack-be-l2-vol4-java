package com.loopers.infrastructure.order;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "order_items")
@Getter
public class OrderItemJpaEntity extends BaseJpaEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private Long productPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    protected OrderItemJpaEntity() {}

    OrderItemJpaEntity(Long id, Long orderId, Long productId, String productName,
            Long productPrice, Integer quantity, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }
}
