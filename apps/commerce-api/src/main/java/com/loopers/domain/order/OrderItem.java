package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "product_price_snapshot", nullable = false)
    private Long productPriceSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    protected OrderItem() {}

    public OrderItem(Long productId, String productNameSnapshot, Long productPriceSnapshot, Integer quantity) {
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.productPriceSnapshot = productPriceSnapshot;
        this.quantity = quantity;
    }

    void assignOrder(Order order) { this.order = order; }

    public long subtotal() {
        return productPriceSnapshot * quantity;
    }

    public Long getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public Long getProductPriceSnapshot() { return productPriceSnapshot; }
    public Integer getQuantity() { return quantity; }
}
