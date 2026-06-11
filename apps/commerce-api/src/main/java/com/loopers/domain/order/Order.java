package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.vo.OrderAmountSnapshot;
import com.loopers.domain.order.vo.Orderer;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user_created_at_id", columnList = "user_id, created_at, id"),
        @Index(name = "idx_orders_created_at_id", columnList = "created_at, id")
    }
)
public class Order extends BaseEntity {

    @Embedded
    private Orderer orderer;

    @Embedded
    private OrderAmountSnapshot amountSnapshot;

    @Column(name = "applied_user_coupon_id")
    private Long appliedUserCouponId;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    private Order(Long userId, OrderItems items) {
        this(userId, items, null, OrderAmountSnapshot.withoutDiscount(items.calculateTotalPrice()));
    }

    private Order(Long userId, OrderItems items, Long userCouponId, OrderAmountSnapshot amountSnapshot) {
        this.orderer = Orderer.of(userId);
        this.items = new ArrayList<>(items.values());
        this.appliedUserCouponId = userCouponId;
        this.amountSnapshot = requireAmountSnapshot(items.calculateTotalPrice(), amountSnapshot);
    }

    public static Order create(Long userId, OrderItems items) {
        return new Order(userId, items);
    }

    public static Order create(Long userId, OrderItems items, Long userCouponId, OrderAmountSnapshot amountSnapshot) {
        validateCouponDiscount(userCouponId, amountSnapshot);
        return new Order(userId, items, userCouponId, amountSnapshot);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Long getUserId() {
        return orderer.userId();
    }

    public boolean isOrderedBy(Long userId) {
        return orderer.isSameUser(userId);
    }

    public long getOrderTotalPrice() {
        return amountSnapshot.orderAmount();
    }

    public Long getAppliedUserCouponId() {
        return appliedUserCouponId;
    }

    public long getDiscountAmount() {
        return amountSnapshot.discountAmount();
    }

    public long getPaymentAmount() {
        return amountSnapshot.paymentAmount();
    }

    private static OrderAmountSnapshot requireAmountSnapshot(long totalPrice, OrderAmountSnapshot amountSnapshot) {
        if (amountSnapshot == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 결제 금액은 비어있을 수 없습니다.");
        }
        if (amountSnapshot.orderAmount() != totalPrice) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품 금액과 결제 스냅샷의 금액이 일치하지 않습니다.");
        }
        return amountSnapshot;
    }

    private static void validateCouponDiscount(Long userCouponId, OrderAmountSnapshot amountSnapshot) {
        if (amountSnapshot == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 결제 금액은 비어있을 수 없습니다.");
        }
        if (userCouponId == null && amountSnapshot.hasDiscount()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 없는 주문에는 할인 금액을 적용할 수 없습니다.");
        }
    }
}
