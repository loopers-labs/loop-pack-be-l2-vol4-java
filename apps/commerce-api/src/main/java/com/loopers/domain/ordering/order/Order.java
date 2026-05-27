package com.loopers.domain.ordering.order;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.ZonedDateTime;

public class Order extends DomainEntity {

    private String userId;

    private Long totalAmount;

    private OrderStatus status;

    private List<OrderLine> lines = new ArrayList<>();

    public Order(String userId, List<OrderLine> lines) {
        validateUserId(userId);
        validateLines(lines);

        this.userId = userId;
        this.lines = new ArrayList<>(lines);
        this.totalAmount = lines.stream()
            .mapToLong(OrderLine::getLineAmount)
            .sum();
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    public static Order reconstruct(
        Long id,
        String userId,
        Long totalAmount,
        OrderStatus status,
        List<OrderLine> lines,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Order order = new Order(userId, lines);
        order.totalAmount = totalAmount == null ? order.totalAmount : totalAmount;
        order.status = status == null ? order.status : status;
        order.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return order;
    }

    public String getUserId() {
        return userId;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isPaymentPending() {
        return status == OrderStatus.PAYMENT_PENDING;
    }

    public boolean isPaid() {
        return status == OrderStatus.PAID;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void markPaid() {
        ensurePaymentPending();
        this.status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        ensurePaymentPending();
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void markCanceled() {
        ensurePaymentPending();
        this.status = OrderStatus.CANCELED;
    }

    private void ensurePaymentPending() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 주문만 상태를 변경할 수 있습니다.");
        }
    }

    private void validateUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateLines(List<OrderLine> value) {
        if (value == null || value.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
    }
}
