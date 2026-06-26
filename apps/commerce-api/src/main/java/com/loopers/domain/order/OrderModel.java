package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderModel {

    private Long id;
    private Long userId;
    private List<OrderLine> orderLines;
    private Long originalTotalPrice;
    private Long discountPrice;
    private Long totalPrice;
    private OrderStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    protected OrderModel() {}

    public OrderModel(Long id, Long userId, List<OrderLine> orderLines, Long originalTotalPrice, Long discountPrice, Long totalPrice, OrderStatus status, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }
        if (totalPrice == null || totalPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상태는 null일 수 없습니다.");
        }
        this.id = id;
        this.userId = userId;
        this.orderLines = orderLines;
        this.originalTotalPrice = originalTotalPrice;
        this.discountPrice = discountPrice;
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderModel create(Long userId, List<OrderLine> orderLines, long discountPrice) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }
        long originalTotalPrice = orderLines.stream().mapToLong(OrderLine::getTotalPrice).sum();
        long totalPrice = Math.max(0, originalTotalPrice - discountPrice);
        return new OrderModel(null, userId, orderLines, originalTotalPrice, discountPrice, totalPrice, OrderStatus.PENDING, null, null);
    }

    /** 결제 성공 시 호출. PENDING → PAID. 이미 PAID 면 멱등(no-op). */
    public void pay() {
        if (this.status == OrderStatus.PAID) {
            return;
        }
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 완료할 수 없는 주문 상태입니다: " + this.status);
        }
        this.status = OrderStatus.PAID;
    }

    /** 결제 실패 시 호출. PENDING → CANCELLED. 이미 CANCELLED 면 멱등(no-op). */
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            return;
        }
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public List<OrderLine> getOrderLines() { return orderLines; }
    public Long getOriginalTotalPrice() { return originalTotalPrice; }
    public Long getDiscountPrice() { return discountPrice; }
    public Long getTotalPrice() { return totalPrice; }
    public OrderStatus getStatus() { return status; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
