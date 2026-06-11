package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_line")
public class OrderLineJpaEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer quantity;

    protected OrderLineJpaEntity() {
    }

    private OrderLineJpaEntity(Long productId, String productName, Long price, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderLineJpaEntity from(OrderLine orderLine) {
        return new OrderLineJpaEntity(
            orderLine.getProductId(),
            orderLine.getProductName(),
            orderLine.getPrice(),
            orderLine.getQuantity()
        );
    }

    public OrderLine toDomain() {
        return OrderLine.reconstruct(getId(), productId, productName, price, quantity);
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
