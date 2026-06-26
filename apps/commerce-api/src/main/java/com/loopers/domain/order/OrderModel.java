package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.tsid.TsidGenerator;

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

    private final Long id;   // 앱 생성 TSID — 생성자에서 즉시 발급(저장 전부터 non-null). 복원 시엔 저장된 값을 그대로.
    private final Long userId;
    private OrderStatus status;
    private Money totalAmount;        // 쿠폰 적용 전 금액 (라인 합계, 01 §7.7)
    private Money discountAmount;     // 할인 금액 (미적용 시 0)
    private Money finalAmount;        // 최종 결제 금액 = totalAmount − discountAmount
    private Long userCouponId;        // 적용된 발급분 ID (없으면 null, 결제 실패 시 원복 참조)
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
        this.id = TsidGenerator.generate();
        this.userId = userId;
        this.paymentMethod = paymentMethod;
        this.status = OrderStatus.PENDING;
        this.totalAmount = Money.zero();
        this.discountAmount = Money.zero();
        this.finalAmount = Money.zero();
        this.userCouponId = null;
    }

    private OrderModel(Long id, Long userId, OrderStatus status, Money totalAmount, Money discountAmount,
                       Money finalAmount, Long userCouponId, PaymentMethod paymentMethod,
                       String failureReason, ZonedDateTime paidAt, List<OrderItem> items) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.userCouponId = userCouponId;
        this.paymentMethod = paymentMethod;
        this.failureReason = failureReason;
        this.paidAt = paidAt;
        this.items.addAll(items);
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static OrderModel reconstitute(Long id, Long userId, OrderStatus status, Money totalAmount,
                                          Money discountAmount, Money finalAmount, Long userCouponId,
                                          PaymentMethod paymentMethod, String failureReason,
                                          ZonedDateTime paidAt, List<OrderItem> items) {
        return new OrderModel(id, userId, status, totalAmount, discountAmount, finalAmount, userCouponId,
                paymentMethod, failureReason, paidAt, items);
    }

    public void addItem(OrderItem item) {
        if (item == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 null일 수 없습니다.");
        }
        this.items.add(item);
    }

    /** lineTotal 합산 → totalAmount (03 §4, Order 책임). 할인 미적용 시 finalAmount = totalAmount. */
    public void calculateTotals() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(Money.zero(), Money::add);
        this.finalAmount = this.totalAmount;
    }

    /**
     * 쿠폰 적용 결과를 반영한다 (UC-17). calculateTotals() 이후 호출.
     * finalAmount = totalAmount − discount, 적용된 발급분 ID를 스냅샷으로 보존한다(원복 참조).
     */
    public void applyDiscount(Long userCouponId, Money discount) {
        this.discountAmount = discount;
        this.finalAmount = this.totalAmount.subtract(discount);
        this.userCouponId = userCouponId;
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

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getFinalAmount() {
        return finalAmount;
    }

    public Long getUserCouponId() {
        return userCouponId;
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
