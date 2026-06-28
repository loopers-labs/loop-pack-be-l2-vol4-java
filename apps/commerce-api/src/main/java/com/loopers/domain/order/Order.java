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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false)
    private Long totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Long userId, Long totalAmount, List<OrderItem> items) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        items.forEach(item -> {
            item.assignOrder(this);
            this.items.add(item);
        });
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public Long getTotalAmount() { return totalAmount; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
