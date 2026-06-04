package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    private Long userId;
    private Long couponIssueId;
    private Long originalAmount;
    private Long discountAmount;
    private Long finalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // [fix] @JoinColumn 누락으로 Hibernate가 join table(orders_items)을 생성 시도 → 오류 발생
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItemModel> items;

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItemModel> items) {
        this(userId, items, null, 0L);
    }

    public OrderModel(Long userId, List<OrderItemModel> items, Long couponIssueId, long discountAmount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 비어있을 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.items = items;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.couponIssueId = couponIssueId;
        this.originalAmount = items.stream().mapToLong(i -> i.getPrice() * i.getQuantity()).sum();
        this.discountAmount = discountAmount;
        this.finalAmount = this.originalAmount - discountAmount;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItemModel> getItems() { return items; }
    public Long getCouponIssueId() { return couponIssueId; }
    public Long getOriginalAmount() { return originalAmount; }
    public Long getDiscountAmount() { return discountAmount; }
    public Long getFinalAmount() { return finalAmount; }
}
