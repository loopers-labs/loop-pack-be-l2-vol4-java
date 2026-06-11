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
    private ZonedDateTime createdAt;

    public Order(String userLoginId, List<OrderLine> orderLines) {
        this(null, userLoginId, OrderStatus.CREATED, orderLines, null);
    }

    private Order(Long id, String userLoginId, OrderStatus status, List<OrderLine> orderLines, ZonedDateTime createdAt) {
        validateUserLoginId(userLoginId);
        validateStatus(status);
        validateOrderLines(orderLines);

        this.id = id;
        this.userLoginId = userLoginId;
        this.status = status;
        this.orderLines.addAll(orderLines);
        this.createdAt = createdAt;
    }

    public static Order reconstruct(
        Long id,
        String userLoginId,
        OrderStatus status,
        List<OrderLine> orderLines,
        ZonedDateTime createdAt
    ) {
        return new Order(id, userLoginId, status, orderLines, createdAt);
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

    public Long getTotalAmount() {
        return orderLines.stream()
            .mapToLong(OrderLine::getAmount)
            .sum();
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
}
