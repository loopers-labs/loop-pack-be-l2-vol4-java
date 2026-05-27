package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_brand_name", nullable = false)
    private String productBrandName;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @Embedded
    private Quantity quantity;

    @Builder
    private OrderItemModel(Long productId, String productName, String productBrandName, Integer unitPrice, Integer rawQuantity) {
        this.productId = productId;
        this.productName = productName;
        this.productBrandName = productBrandName;
        this.unitPrice = unitPrice;
        this.quantity = Quantity.from(rawQuantity);
    }

    public void assignOrder(Long orderId) {
        this.orderId = orderId;
    }

    public int totalPrice() {
        return unitPrice * quantity.value();
    }
}
