package com.loopers.tddstudy.domain.order;

import jakarta.persistence.*;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private Long productId;
    private String productNameSnapshot;
    private int priceSnapshot;
    private int quantity;

    protected OrderItem() {}

    public OrderItem(Long productId, String productNameSnapshot, int priceSnapshot, int quantity) {
        validateQuantity(quantity);
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.quantity = quantity;
    }

    public int lineAmount() {
        return priceSnapshot * quantity;
    }


    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public int getPriceSnapshot() { return priceSnapshot; }
    public int getQuantity() { return quantity; }
}
