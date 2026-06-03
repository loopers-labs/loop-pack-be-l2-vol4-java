package com.loopers.infrastructure.ordering.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.ordering.order.OrderLine;
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

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_amount", nullable = false)
    private Long lineAmount;

    protected OrderLineJpaEntity() {}

    private OrderLineJpaEntity(Long productId, String productName, Long unitPrice, Integer quantity, Long lineAmount) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineAmount = lineAmount;
    }

    public static OrderLineJpaEntity from(OrderLine line) {
        return new OrderLineJpaEntity(
            line.getProductId(),
            line.getProductName(),
            line.getUnitPrice(),
            line.getQuantity(),
            line.getLineAmount()
        );
    }

    public OrderLine toDomain() {
        return OrderLine.reconstruct(
            getId(),
            productId,
            productName,
            unitPrice,
            quantity,
            lineAmount,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }
}
