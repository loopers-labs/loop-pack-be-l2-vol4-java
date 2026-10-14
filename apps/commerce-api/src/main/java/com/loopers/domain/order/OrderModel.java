package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order Aggregate — OrderItem 들을 포함하고 총액·할인·상태 전이를 관리한다.
 * <p>
 * 쿠폰 적용 시 originalPrice / discountAmount / finalPrice 스냅샷을 모두 보존한다.
 */
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "original_price", nullable = false)
    private Long originalPrice;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId, List<OrderItemModel> items) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1 개 이상이어야 합니다.");
        }
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.originalPrice = computeTotal(this.items);
        this.discountAmount = 0L;
        this.finalPrice = this.originalPrice;
        this.userCouponId = null;
        this.status = OrderStatus.PENDING;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getOriginalPrice() {
        return originalPrice;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalPrice() {
        return finalPrice;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public List<OrderItemModel> getItems() {
        return Collections.unmodifiableList(items);
    }

    public long totalAmount() {
        return finalPrice;
    }

    /**
     * 쿠폰을 적용해 할인을 반영한다.
     * PENDING 상태에서만 가능하며, 할인액은 originalPrice 를 초과할 수 없다.
     */
    public void applyCoupon(Long userCouponId, long discountAmount) {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "PENDING 상태가 아닌 주문에는 쿠폰을 적용할 수 없습니다.");
        }
        if (userCouponId == null || userCouponId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID 는 양수여야 합니다.");
        }
        if (discountAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인액은 0 이상이어야 합니다.");
        }
        if (discountAmount > this.originalPrice) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인액이 주문 금액을 초과할 수 없습니다.");
        }
        this.userCouponId = userCouponId;
        this.discountAmount = discountAmount;
        this.finalPrice = this.originalPrice - discountAmount;
    }

    public void markPaid() {
        assertNotTerminal();
        this.status = OrderStatus.PAID;
    }

    public void markFailed() {
        assertNotTerminal();
        this.status = OrderStatus.FAILED;
    }

    public void markCancelled() {
        assertNotTerminal();
        this.status = OrderStatus.CANCELLED;
    }

    private void assertNotTerminal() {
        if (this.status != null && this.status.isTerminal()) {
            throw new CoreException(ErrorType.CONFLICT,
                "이미 종료 상태(" + this.status + ")인 주문은 다시 전이할 수 없습니다.");
        }
    }

    private static long computeTotal(List<OrderItemModel> items) {
        long total = 0L;
        for (OrderItemModel item : items) {
            total += item.subtotal();
        }
        return total;
    }
}
