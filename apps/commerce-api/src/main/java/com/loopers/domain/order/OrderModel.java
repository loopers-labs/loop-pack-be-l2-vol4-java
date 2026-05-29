package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.order.vo.Money;
import com.loopers.support.Guard;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uq_order_order_number", columnNames = {"order_number"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseEntity {

    private static final int PAYMENT_EXPIRY_MINUTES = 15;

    @Column(name = "order_number", nullable = false, length = 20)
    private String orderNumber;

    @Column(nullable = false)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalMoney;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();

    public OrderModel(Long userId) {
        Guard.notNull(userId, "사용자 ID는 필수입니다.");
        this.orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        this.userId = userId;
        this.totalMoney = new Money(0L);
        this.status = OrderStatus.REQUESTED;
    }

    public void updateTotal(Money total) {
        Guard.notNull(total, "주문 금액은 필수입니다.");
        this.totalMoney = total;
    }

    public void complete() {
        if (this.status != OrderStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상태에서만 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status != OrderStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상태에서만 취소할 수 있습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isPayable() {
        return this.status == OrderStatus.REQUESTED
                && ZonedDateTime.now().isBefore(getCreatedAt().plusMinutes(PAYMENT_EXPIRY_MINUTES));
    }

    public boolean isExpirable() {
        return this.status == OrderStatus.REQUESTED
                && ZonedDateTime.now().isAfter(getCreatedAt().plusMinutes(PAYMENT_EXPIRY_MINUTES));
    }

    public static List<OrderItemInput> merge(List<OrderItemInput> inputs) {
        return inputs.stream()
                .collect(Collectors.groupingBy(
                        OrderItemInput::stockId,
                        Collectors.summingInt(OrderItemInput::quantity)
                ))
                .entrySet().stream()
                .map(e -> new OrderItemInput(e.getKey(), e.getValue()))
                .toList();
    }

    public void addItem(OrderItemModel item) {
        this.items.add(item);
    }

    public String getOrderNumber() { return orderNumber; }

    public Long getUserId() { return userId; }

    public Money getTotalMoney() { return totalMoney; }

    public OrderStatus getStatus() { return status; }

    public List<OrderItemModel> getItems() { return items; }
}
