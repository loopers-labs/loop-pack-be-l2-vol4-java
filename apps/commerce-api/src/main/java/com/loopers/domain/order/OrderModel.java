package com.loopers.domain.order;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class OrderModel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "coupon_issue_id")
    private Long couponIssueId;

    @Column(name = "total_original_amount", nullable = false)
    private java.math.BigDecimal totalOriginalAmount;

    @Column(name = "total_discount_amount", nullable = false)
    private java.math.BigDecimal totalDiscountAmount;

    @Column(name = "total_payment_amount", nullable = false)
    private java.math.BigDecimal totalPaymentAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItemModel> items = new ArrayList<>();

    public OrderModel(Long userId) {
        this.userId = userId;
        this.status = OrderStatus.COMPLETED;
        this.totalOriginalAmount = java.math.BigDecimal.ZERO;
        this.totalDiscountAmount = java.math.BigDecimal.ZERO;
        this.totalPaymentAmount = java.math.BigDecimal.ZERO;
    }

    public OrderModel(Long userId, Long couponIssueId, java.math.BigDecimal totalOriginalAmount, java.math.BigDecimal totalDiscountAmount, java.math.BigDecimal totalPaymentAmount) {
        this.userId = userId;
        this.couponIssueId = couponIssueId;
        this.totalOriginalAmount = totalOriginalAmount;
        this.totalDiscountAmount = totalDiscountAmount;
        this.totalPaymentAmount = totalPaymentAmount;
        this.status = OrderStatus.PENDING;
    }

    public void addItem(OrderItemModel item) {
        this.items.add(item);
    }

    public void complete() {
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELED;
    }
}
