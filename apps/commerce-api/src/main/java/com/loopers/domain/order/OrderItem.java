package com.loopers.domain.order;

import com.loopers.domain.BaseDomain;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
public class OrderItem extends BaseDomain {

    private Long orderId;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private int quantity;

    public OrderItem(Long productId, String productName, BigDecimal productPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public OrderItem(Long orderId, Long productId, String productName, BigDecimal productPrice, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public OrderItem withOrderId(Long orderId) {
        return new OrderItem(orderId, this.productId, this.productName, this.productPrice, this.quantity);
    }

    public OrderItem(Long id, Long orderId, Long productId, String productName, BigDecimal productPrice,
                     int quantity, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }
}
