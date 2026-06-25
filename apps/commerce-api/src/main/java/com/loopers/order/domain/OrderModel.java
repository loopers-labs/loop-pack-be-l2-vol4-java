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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    private Long userId;
    private String loginId;
    private String idempotencyKey;
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
        this(userId, null, items, null, 0L);
    }

    public OrderModel(Long userId, List<OrderItemModel> items, Long couponIssueId, long discountAmount) {
        this(userId, null, items, couponIssueId, discountAmount);
    }

    public OrderModel(Long userId, String loginId, List<OrderItemModel> items, Long couponIssueId, long discountAmount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 비어있을 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.loginId = loginId;
        this.items = items;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.couponIssueId = couponIssueId;
        this.originalAmount = items.stream().mapToLong(i -> i.getPrice() * i.getQuantity()).sum();
        this.discountAmount = discountAmount;
        this.finalAmount = this.originalAmount - discountAmount;
    }

    // [fix] PAYMENT_FAILED 주문은 재결제할 방법이 없어 영구 고착되던 문제 수정 — 실패 사유와 무관하게 재결제 허용
    public void startPayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제를 시작할 수 없는 주문 상태입니다.");
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.idempotencyKey = timestamp + "-" + uuid;
        this.status = OrderStatus.IN_PAYMENT;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void failPayment() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public Long getUserId() { return userId; }
    public String getLoginId() { return loginId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderStatus getStatus() { return status; }
    public List<OrderItemModel> getItems() { return items; }
    public Long getCouponIssueId() { return couponIssueId; }
    public Long getOriginalAmount() { return originalAmount; }
    public Long getDiscountAmount() { return discountAmount; }
    public Long getFinalAmount() { return finalAmount; }
}
