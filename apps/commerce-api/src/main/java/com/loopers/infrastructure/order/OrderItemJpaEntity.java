package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private OrderItemJpaEntity(Long productId, String productName, long unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItemJpaEntity of(Long productId, String productName, long unitPrice, int quantity) {
        return new OrderItemJpaEntity(productId, productName, unitPrice, quantity);
    }
}
