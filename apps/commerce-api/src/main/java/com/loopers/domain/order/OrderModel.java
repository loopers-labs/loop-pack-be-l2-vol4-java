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
    @AttributeOverride(name = "amount", column = @Column(name = "total_price"))
    private Money totalPrice;

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

    public void confirmTotalPrice() {
        this.totalPrice = calculateTotalPriceAsMoney();
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

    /** 원시 타입 총액 (DTO/응답용). null 가능 (확정 전). */
    public Long getTotalPrice() {
        return totalPrice == null ? null : totalPrice.getAmount();
    }

    public ZonedDateTime getOrderedAt() {
        return orderedAt;
    }

    public List<OrderItemModel> getItems() {
        return items;
    }
}
