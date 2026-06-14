package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.order.domain.vo.OrderItemSnapshot;
import com.loopers.order.domain.vo.OrderPrice;
import com.loopers.order.domain.vo.OrderQuantity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @Embedded
    private OrderItemSnapshot snapshot;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "unit_price", nullable = false))
    private OrderPrice unitPrice;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private OrderQuantity quantity;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_price", nullable = false))
    private OrderPrice totalPrice;

    private OrderItem(
        OrderItemSnapshot snapshot,
        OrderPrice unitPrice,
        OrderQuantity quantity
    ) {
        this.snapshot = snapshot;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = unitPrice.multiply(quantity);
    }

    public static OrderItem create(
        Long brandId,
        String brandName,
        Long productId,
        String productName,
        long unitPrice,
        int quantity
    ) {
        OrderItemSnapshot snapshot = OrderItemSnapshot.of(brandId, brandName, productId, productName);
        OrderPrice orderPrice = OrderPrice.of(unitPrice);
        OrderQuantity orderQuantity = OrderQuantity.of(quantity);
        return new OrderItem(snapshot, orderPrice, orderQuantity);
    }

    public Long getBrandId() {
        return snapshot.brandId();
    }

    public String getBrandName() {
        return snapshot.brandName();
    }

    public Long getProductId() {
        return snapshot.productId();
    }

    public String getProductName() {
        return snapshot.productName();
    }

    public long getUnitPrice() {
        return unitPrice.value();
    }

    public int getQuantity() {
        return quantity.value();
    }

    public long getTotalPrice() {
        return totalPrice.value();
    }

    OrderPrice totalPrice() {
        return totalPrice;
    }
}
