package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_amount", nullable = false)
    private Long finalAmount;

    @Column(name = "used_coupon_id")
    private Long usedCouponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    protected OrderModel() {}

    private OrderModel(Long userId, Long totalAmount, Long discountAmount, Long finalAmount,
                       Long usedCouponId, OrderStatus status) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.usedCouponId = usedCouponId;
        this.status = status;
    }

    public static OrderModel create(Long userId, OrderLines lines) {
        return create(userId, lines, 0L, null);
    }

    public static OrderModel create(Long userId, OrderLines lines, long discountAmount, Long usedCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID 는 비어있을 수 없습니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.EMPTY_ORDER_ITEMS, "주문 항목이 비어있습니다.");
        }
        if (discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 음수일 수 없습니다.");
        }
        long total = lines.totalAmount();
        if (discountAmount > total) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.");
        }
        return new OrderModel(userId, total, discountAmount, total - discountAmount, usedCouponId, OrderStatus.CREATED);
    }

    /**
     * 결제를 시작한다. 최초 결제(CREATED)와 재결제(PAYMENT_FAILED)에서만 PAYMENT_PENDING 으로 전이한다.
     * 이미 결제가 진행 중이거나(PAYMENT_PENDING) 완료된(PAID) 주문은 결제를 시작할 수 없다.
     */
    public void startPayment() {
        if (status != OrderStatus.CREATED && status != OrderStatus.PAYMENT_FAILED) {
            throw new CoreException(ErrorType.ORDER_NOT_PAYABLE, "결제를 시작할 수 없는 주문 상태입니다. [status = " + status + "]");
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    /**
     * 결제 완료를 반영한다. 웹훅·배치가 중복 수렴해도 안전하도록 이미 PAID 면 멱등하게 no-op 한다.
     */
    public void markPaid() {
        if (status == OrderStatus.PAID) {
            return;
        }
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.INVALID_ORDER_STATUS, "결제 대기 상태에서만 결제 완료할 수 있습니다. [status = " + status + "]");
        }
        this.status = OrderStatus.PAID;
    }

    /**
     * 결제 실패를 반영한다. 웹훅·배치가 중복 수렴해도 안전하도록 이미 PAYMENT_FAILED 면 멱등하게 no-op 한다.
     */
    public void markPaymentFailed() {
        if (status == OrderStatus.PAYMENT_FAILED) {
            return;
        }
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.INVALID_ORDER_STATUS, "결제 대기 상태에서만 결제 실패를 반영할 수 있습니다. [status = " + status + "]");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return finalAmount;
    }

    public Long getUsedCouponId() {
        return usedCouponId;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
