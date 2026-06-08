package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "original_amount"))
    private Money originalAmount;   // 쿠폰 적용 전 금액 (스냅샷)

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount"))
    private Money discountAmount;   // 쿠폰 할인 금액 (스냅샷)

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_price"))
    private Money totalPrice;       // 최종 결제 금액 (= 원금 - 할인, 스냅샷)

    @Column(name = "ordered_at", nullable = false)
    private ZonedDateTime orderedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.orderedAt = ZonedDateTime.now();
    }

    public void addItem(OrderItemModel item) {
        this.items.add(item);
    }

    /** 총액 계산 (Money VO 반환). 도메인 내부 협력용. */
    Money calculateTotalPriceAsMoney() {
        return items.stream()
            .map(OrderItemModel::calculateSubtotalAsMoney)
            .reduce(Money.zero(), Money::plus);
    }

    /** 총액 (DTO/응답용 — Long). */
    public Long calculateTotalPrice() {
        return calculateTotalPriceAsMoney().getAmount();
    }

    /**
     * 쿠폰 미적용 기준으로 금액 스냅샷을 확정한다 (원금 = 최종, 할인 0).
     * 쿠폰 적용 시에는 이후 {@link #applyDiscount(Money)} 로 갱신한다.
     */
    public void confirmAmounts() {
        this.originalAmount = calculateTotalPriceAsMoney();
        this.discountAmount = Money.zero();
        this.totalPrice = this.originalAmount;
    }

    /**
     * 쿠폰 할인을 적용해 최종 결제 금액을 확정한다.
     *
     * <p>{@link #confirmAmounts()} 이후 호출해야 한다. 할인액이 원금을 초과하면 원금까지만
     * 할인하여 최종 금액이 음수가 되지 않도록 한다(이중 방어 — 쿠폰 도메인에서도 캡한다).
     */
    public void applyDiscount(Money discount) {
        if (this.originalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "원금 확정 후 할인을 적용할 수 있습니다.");
        }
        Money capped = this.originalAmount.isGreaterThanOrEqual(discount) ? discount : this.originalAmount;
        this.discountAmount = capped;
        this.totalPrice = this.originalAmount.minus(capped);
    }

    public void complete() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기 중인 주문만 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기 중인 주문만 취소할 수 있습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** 최종 결제 금액 (DTO/응답용). null 가능 (확정 전). */
    public Long getTotalPrice() {
        return totalPrice == null ? null : totalPrice.getAmount();
    }

    /** 쿠폰 적용 전 원금 (DTO/응답용). null 가능 (확정 전). */
    public Long getOriginalAmount() {
        return originalAmount == null ? null : originalAmount.getAmount();
    }

    /** 쿠폰 할인 금액 (DTO/응답용). null 가능 (확정 전). */
    public Long getDiscountAmount() {
        return discountAmount == null ? null : discountAmount.getAmount();
    }

    public ZonedDateTime getOrderedAt() {
        return orderedAt;
    }

    public List<OrderItemModel> getItems() {
        return items;
    }
}
