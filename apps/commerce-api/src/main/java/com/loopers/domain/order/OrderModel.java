package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderModel {

    private Long id;
    private Long userId;
    private List<OrderLine> orderLines;
    private Long totalPrice;
    private OrderStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    protected OrderModel() {}

    public OrderModel(Long id, Long userId, List<OrderLine> orderLines, Long totalPrice, OrderStatus status, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
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
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderModel create(Long userId, List<OrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }
        long totalPrice = orderLines.stream().mapToLong(OrderLine::getTotalPrice).sum();
        return new OrderModel(null, userId, orderLines, totalPrice, OrderStatus.PENDING, null, null);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public List<OrderLine> getOrderLines() { return orderLines; }
    public Long getTotalPrice() { return totalPrice; }
    public OrderStatus getStatus() { return status; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
