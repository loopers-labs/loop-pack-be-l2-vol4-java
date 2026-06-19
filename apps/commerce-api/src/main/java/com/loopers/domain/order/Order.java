package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {

    private Long id;
    private String userLoginId;
    private OrderStatus status;
    private List<OrderLine> orderLines = new ArrayList<>();
    private Long discountAmount;
    private ZonedDateTime createdAt;

    public Order(String userLoginId, List<OrderLine> orderLines) {
        this(userLoginId, orderLines, 0L);
    }

    public Order(String userLoginId, List<OrderLine> orderLines, Long discountAmount) {
        this(null, userLoginId, OrderStatus.CREATED, orderLines, discountAmount, null);
    }

    private Order(
        Long id,
        String userLoginId,
        OrderStatus status,
        List<OrderLine> orderLines,
        Long discountAmount,
        ZonedDateTime createdAt
    ) {
        validateUserLoginId(userLoginId);
        validateStatus(status);
        validateOrderLines(orderLines);
        validateDiscountAmount(orderLines, discountAmount);

        this.id = id;
        this.userLoginId = userLoginId;
        this.status = status;
        this.orderLines.addAll(orderLines);
        this.discountAmount = discountAmount;
        this.createdAt = createdAt;
    }

    public static Order reconstruct(
        Long id,
        String userLoginId,
        OrderStatus status,
        List<OrderLine> orderLines,
        Long discountAmount,
        ZonedDateTime createdAt
    ) {
        return new Order(id, userLoginId, status, orderLines, discountAmount, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderLine> getOrderLines() {
        return Collections.unmodifiableList(orderLines);
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getOriginalAmount() {
        return orderLines.stream()
            .mapToLong(OrderLine::getAmount)
            .sum();
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return getOriginalAmount() - discountAmount;
    }

    public Long getTotalAmount() {
        return getFinalAmount();
    }

    public void applyDiscount(Long discountAmount) {
        validateDiscountAmount(orderLines, discountAmount);
        this.discountAmount = discountAmount;
    }

    private void validateUserLoginId(String userLoginId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 로그인 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateStatus(OrderStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상태는 비어있을 수 없습니다.");
        }
    }

    private void validateOrderLines(List<OrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }
    }

    private void validateDiscountAmount(List<OrderLine> orderLines, Long discountAmount) {
        if (discountAmount == null || discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.");
        }
        Long originalAmount = orderLines.stream()
            .mapToLong(OrderLine::getAmount)
            .sum();
        if (discountAmount > originalAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액보다 클 수 없습니다.");
        }
    }
}
