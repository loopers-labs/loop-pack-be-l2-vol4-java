package com.loopers.infrastructure.order;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Embeddable
@Table(name = "order_items")
@Getter
public class OrderItemJpaVO {

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

    protected OrderItemJpaVO() {}

    OrderItemJpaVO(Long orderId, Long productId, String productName,
                   Long productPrice, Integer quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }
}
