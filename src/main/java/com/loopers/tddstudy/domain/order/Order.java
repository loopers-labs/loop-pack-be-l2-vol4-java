package com.loopers.tddstudy.domain.order;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String status;
    private int totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    //쿠폰관련
    private int originalAmount;   // 할인 전 금액
    private int discountAmount;   // 할인 금액
    private Long userCouponId;    // 사용한 쿠폰 ID (nullable)


    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Long userId) {
        validateUserId(userId);
        this.userId = userId;
        this.status = "PENDING";
        this.totalAmount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }



    public void addItem(OrderItem item) {
        this.items.add(item);
        this.totalAmount = calculateTotal();
        this.updatedAt = LocalDateTime.now();
    }

    public void markPaid() {
        this.status = "PAID";
        this.updatedAt = LocalDateTime.now();
    }

    public void applyDiscount(int originalAmount, int discountAmount) {
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = originalAmount - discountAmount;
    }

    public void markFailed() {
        this.status = "FAILED";
        this.updatedAt = LocalDateTime.now();
    }

    public int calculateTotal() {
        return items.stream().mapToInt(OrderItem::lineAmount).sum();
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("유저 ID는 필수입니다.");
        }
    }




    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getTotalAmount() { return totalAmount; }
    public List<OrderItem> getItems() { return items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public int getOriginalAmount() { return originalAmount; }
    public int getDiscountAmount() { return discountAmount; }
    public Long getUserCouponId() { return userCouponId; }
}
