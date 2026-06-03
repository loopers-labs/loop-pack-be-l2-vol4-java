package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order Aggregate 루트 — 순수 도메인 객체. 상태 머신/집계 같은 비즈니스 규칙만 보유하고
 * 영속 기술(JPA)에는 의존하지 않는다. JPA 매핑은 infrastructure.order.OrderEntity가 담당하고,
 * 도메인 ↔ 엔티티 변환은 OrderEntityMapper가 처리한다.
 */
public class OrderModel {

    private final Long id;   // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private final Long userId;
    private OrderStatus status;
    private Money totalAmount;
    private final PaymentMethod paymentMethod;
    private String failureReason;
    private ZonedDateTime paidAt;
    private final List<OrderItem> items = new ArrayList<>();

    public OrderModel(Long userId, PaymentMethod paymentMethod) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (paymentMethod == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 수단은 null일 수 없습니다.");
        }
        this.id = null;
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.status = OrderStatus.PENDING;
        this.totalAmount = Money.zero();
    }

    private OrderModel(Long id, Long userId, OrderStatus status, Money totalAmount, PaymentMethod paymentMethod,
                       String failureReason, ZonedDateTime paidAt, List<OrderItem> items) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.failureReason = failureReason;
        this.paidAt = paidAt;
        this.items.addAll(items);
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static OrderModel reconstitute(Long id, Long userId, OrderStatus status, Money totalAmount,
                                          PaymentMethod paymentMethod, String failureReason,
                                          ZonedDateTime paidAt, List<OrderItem> items) {
        return new OrderModel(id, userId, status, totalAmount, paymentMethod, failureReason, paidAt, items);
    }

    public void addItem(OrderItem item) {
        if (item == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 null일 수 없습니다.");
        }
        this.items.add(item);
    }

    /** lineTotal 합산 → totalAmount (03 §4, Order 책임). */
    public void calculateTotals() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(Money.zero(), Money::add);
    }

    /** PENDING → PAID. 다른 상태에서 호출 시 CONFLICT (04 §5.1). */
    public void markPaid() {
        requirePending();
        this.status = OrderStatus.PAID;
        this.paidAt = ZonedDateTime.now();
    }

    /** PENDING → FAILED. 다른 상태에서 호출 시 CONFLICT. 재고 원복은 Service 책임. */
    public void markFailed(String reason) {
        requirePending();
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
    }

    private void requirePending() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태에서만 결제 결과를 반영할 수 있습니다. (현재: " + this.status + ")");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public ZonedDateTime getPaidAt() {
        return paidAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
