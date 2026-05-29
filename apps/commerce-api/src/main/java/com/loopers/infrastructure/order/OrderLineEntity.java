package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_line")
public class OrderLineEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    private String productName;
    private Long productPrice;
    private Integer quantity;
    private Long totalPrice;

    private OrderLineEntity(OrderEntity order, Long productId, String productName, Long productPrice, Integer quantity, Long totalPrice) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
    }

    public static OrderLineEntity from(OrderLine line, OrderEntity order) {
        return new OrderLineEntity(order, line.getProductId(), line.getProductName(), line.getProductPrice(), line.getQuantity(), line.getTotalPrice());
    }

    public OrderLine toDomain() {
        return new OrderLine(
            getId(),
            productId,
            productName,
            productPrice,
            quantity,
            totalPrice,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
