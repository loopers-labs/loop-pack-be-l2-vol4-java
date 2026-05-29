package com.loopers.domain.order;

import jakarta.persistence.*;

/**
 * 주문 항목. Order Aggregate 내부 Entity.
 * 별도 Repository 없이 Order를 통해서만 접근한다.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Embedded
    private ProductSnapshot snapshot;

    protected OrderItem() {}

    public OrderItem(Long productId, int quantity, ProductSnapshot snapshot) {
        this.productId = productId;
        this.quantity = quantity;
        this.snapshot = snapshot;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public ProductSnapshot getSnapshot() { return snapshot; }
}
