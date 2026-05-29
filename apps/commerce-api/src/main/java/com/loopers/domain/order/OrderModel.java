package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    private OrderModel(Long userId, List<OrderItemModel> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 필수입니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.totalAmount = calculateTotal(this.items);
        this.status = OrderStatus.PENDING;
    }

    public static OrderModel create(Long userId, List<OrderItemModel> items) {
        return new OrderModel(userId, items);
    }

    private static Money calculateTotal(List<OrderItemModel> items) {
        return items.stream()
                .map(OrderItemModel::subtotal)
                .reduce(Money.ZERO, Money::plus);
    }

    /** 결제 성공 시 호출 (다음 라운드) */
    public void confirm() {
        this.status = OrderStatus.COMPLETED;
    }

    /** 결제/검증 실패 시 호출 (다음 라운드) */
    public void fail() {
        this.status = OrderStatus.FAILED;
    }

    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }

    /** 외부에서 컬렉션을 직접 못 바꾸도록 읽기 전용으로 반환 */
    public List<OrderItemModel> getItems() {
        return Collections.unmodifiableList(items);
    }
}
