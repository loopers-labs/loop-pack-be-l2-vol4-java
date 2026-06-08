package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Embedded
    @AttributeOverride(name = "amount",
        column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    public OrderItem(Long productId, String productName, Money unitPrice, Quantity quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public Money lineAmount() {
        return unitPrice.multiply(quantity.getValue());
    }

    public void assignOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
