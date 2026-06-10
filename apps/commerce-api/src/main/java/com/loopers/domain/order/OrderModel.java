package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.ShippingInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "pg_amount", nullable = false)
    private Long pgAmount;

    @Column(name = "coupon_id", columnDefinition = "BINARY(16)")
    private UUID couponId;

    @Embedded
    private ShippingInfo shippingInfo;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemModel> items = new ArrayList<>();

    public OrderModel(UUID userId, String receiverName, String receiverPhone,
                      String zipCode, String address, String detailAddress) {
        this.userId = userId;
        this.shippingInfo = new ShippingInfo(receiverName, receiverPhone, zipCode, address, detailAddress);
        this.originalAmount = 0L;
        this.discountAmount = 0L;
        this.pgAmount = 0L;
    }

    public void addItem(OrderItemModel item) {
        items.add(item);
        this.originalAmount += item.getSubtotal();
        this.pgAmount = this.originalAmount - this.discountAmount;
    }

    /** 쿠폰 할인 적용 — 할인액은 호출 측에서 cap/floor 처리해 전달한다. pgAmount = 원금 - 할인. */
    public void applyCoupon(UUID couponId, long discountAmount) {
        this.couponId = couponId;
        this.discountAmount = discountAmount;
        this.pgAmount = this.originalAmount - discountAmount;
    }

    public boolean isPending() {
        return this.status == OrderStatus.PENDING;
    }

    public void confirm(Long paymentAmount) {
        if (!isPending()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태 주문만 확정할 수 있습니다.");
        }
        if (!this.pgAmount.equals(paymentAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void fail() {
        if (!isPending()) {
            return; // 멱등
        }
        this.status = OrderStatus.FAILED;
    }

    public void cancel() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "CONFIRMED 상태 주문만 취소할 수 있습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }
}
