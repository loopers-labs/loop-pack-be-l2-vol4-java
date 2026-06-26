package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity(name = "Orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order extends BaseEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id", nullable = false))
    @BatchSize(size = 100)
    private List<OrderItem> items = new ArrayList<>();

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "original_amount", nullable = false))
    private Money originalAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    private Order(Long userId, OrderItems items,
                  Money originalAmount, Money discountAmount, Money totalAmount, OrderStatus status) {
        validateUserId(userId);
        validateItems(items);
        validateAmount(originalAmount, "주문 총액");
        validateAmount(discountAmount, "할인액");
        validateAmount(totalAmount, "최종 금액");
        validateStatus(status);
        if (!originalAmount.isGreaterThanOrEqual(discountAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인액은 주문 금액을 초과할 수 없습니다.");
        }
        this.userId = userId;
        this.items = items.asList();
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public static Order create(Long userId, OrderItems items, Money discountAmount) {
        validateItems(items);
        Money original = items.totalAmount();
        Money total = original.subtract(discountAmount);
        return new Order(userId, items, original, discountAmount, total, OrderStatus.CREATED);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isOwnedBy(Long userId) {
        if (userId == null) {
            return false;
        }
        return this.userId.equals(userId);
    }

    /**
     * 결제 성공으로 주문을 확정한다. 이미 PAID 면 멱등하게 무시하고 {@code false} 를 반환한다.
     *
     * @return 이번 호출로 CREATED → PAID 전이가 실제로 일어났으면 {@code true}
     */
    public boolean pay() {
        if (status == OrderStatus.PAID) {
            return false;
        }
        if (status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제할 수 없는 주문 상태입니다.");
        }
        this.status = OrderStatus.PAID;
        return true;
    }

    /**
     * 주문 시각은 영속 시점에 BaseEntity 가 기록하는 createdAt(ZonedDateTime) 을
     * 서비스 표준 시간대(Asia/Seoul) 의 LocalDateTime 으로 노출한다.
     */
    public LocalDateTime getOrderedAt() {
        return getCreatedAt() == null
                ? null
                : getCreatedAt().withZoneSameInstant(ZONE).toLocalDateTime();
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 비어있을 수 없습니다.");
        }
    }

    private static void validateItems(OrderItems items) {
        if (items == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
    }

    private static void validateAmount(Money amount, String label) {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, label + "은 비어있을 수 없습니다.");
        }
    }

    private static void validateStatus(OrderStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상태는 비어있을 수 없습니다.");
        }
    }
}
