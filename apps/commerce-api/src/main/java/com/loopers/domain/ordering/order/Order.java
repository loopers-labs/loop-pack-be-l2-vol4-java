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

    private Long originalAmount;

    private Long discountAmount;

    private Long finalAmount;

    private Long couponId;

    private OrderStatus status;

    private List<OrderLine> lines = new ArrayList<>();

    public Order(String userId, List<OrderLine> lines) {
        this(userId, lines, null, 0L);
    }

    public Order(String userId, List<OrderLine> lines, Long couponId, Long discountAmount) {
        validateUserId(userId);
        validateLines(lines);

        this.userId = userId;
        this.lines = new ArrayList<>(lines);
        this.originalAmount = lines.stream()
            .mapToLong(OrderLine::getLineAmount)
            .sum();
        validateDiscountAmount(discountAmount, originalAmount);
        this.discountAmount = discountAmount;
        this.finalAmount = originalAmount - discountAmount;
        this.couponId = couponId;
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    public static Order reconstruct(
        Long id,
        String userId,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long couponId,
        OrderStatus status,
        List<OrderLine> lines,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Order order = new Order(userId, lines, couponId, discountAmount == null ? 0L : discountAmount);
        order.originalAmount = originalAmount == null ? order.originalAmount : originalAmount;
        order.finalAmount = finalAmount == null ? order.finalAmount : finalAmount;
        order.status = status == null ? order.status : status;
        order.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return order;
    }

    public String getUserId() {
        return userId;
    }

    public Long getOriginalAmount() {
        return originalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return finalAmount;
    }

    public Long getCouponId() {
        return couponId;
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

    public boolean requiresPayment() {
        return finalAmount > 0;
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

    private void validateDiscountAmount(Long value, Long originalAmount) {
        if (value == null || value < 0 || value > originalAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액 범위 안이어야 합니다.");
        }
    }
}
