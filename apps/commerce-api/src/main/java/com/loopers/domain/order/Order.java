package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Long userId, Long totalPrice, List<OrderItem> items) {
        this.userId = userId;
        this.totalPrice = totalPrice;
        items.forEach(item -> {
            item.assignOrder(this);
            this.items.add(item);
        });
    }

    public Long getUserId() { return userId; }
    public Long getTotalPrice() { return totalPrice; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
