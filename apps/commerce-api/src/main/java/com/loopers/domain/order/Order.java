package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class Order {

    private final Long id;
    private final Long userId;
    private final List<OrderItem> items;
    private final Money totalAmount;
    private final OrderStatus status;
    private final LocalDateTime orderedAt;

    private Order(Long id,
                  Long userId,
                  List<OrderItem> items,
                  Money totalAmount,
                  OrderStatus status,
                  LocalDateTime orderedAt) {
        validateUserId(userId);
        validateItems(items);
        validateTotalAmount(totalAmount);
        validateStatus(status);
        validateOrderedAt(orderedAt);
        this.id = id;
        this.userId = userId;
        this.items = List.copyOf(items);
        this.totalAmount = totalAmount;
        this.status = status;
        this.orderedAt = orderedAt;
    }

    public static Order create(Long userId, List<OrderItem> items) {
        validateItems(items);
        Money total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.of(0), Money::add);
        return new Order(null, userId, items, total, OrderStatus.CREATED, LocalDateTime.now());
    }

    public static Order restore(Long id,
                                Long userId,
                                List<OrderItem> items,
                                Money totalAmount,
                                OrderStatus status,
                                LocalDateTime orderedAt) {
        if (id == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 비어있을 수 없습니다.");
        }
        return new Order(id, userId, items, totalAmount, status, orderedAt);
    }

    public boolean isOwnedBy(Long userId) {
        if (userId == null) {
            return false;
        }
        return this.userId.equals(userId);
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 비어있을 수 없습니다.");
        }
    }

    private static void validateItems(List<OrderItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
    }

    private static void validateTotalAmount(Money totalAmount) {
        if (totalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 총액은 비어있을 수 없습니다.");
        }
    }

    private static void validateStatus(OrderStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상태는 비어있을 수 없습니다.");
        }
    }

    private static void validateOrderedAt(LocalDateTime orderedAt) {
        if (orderedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 시각은 비어있을 수 없습니다.");
        }
    }
}
