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

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long productPrice;

    @Column(nullable = false)
    private Integer quantity;

    protected OrderItem() {}

    public OrderItem(Long productId, String productName, Long productPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    void assignOrder(Order order) { this.order = order; }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Long getProductPrice() { return productPrice; }
    public Integer getQuantity() { return quantity; }
}
