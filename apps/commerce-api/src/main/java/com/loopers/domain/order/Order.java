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
import org.springframework.util.CollectionUtils;

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
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    private Order(Long userId, List<OrderItem> items, Money totalAmount, OrderStatus status) {
        validateUserId(userId);
        validateItems(items);
        validateTotalAmount(totalAmount);
        validateStatus(status);
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public static Order create(Long userId, List<OrderItem> items) {
        validateItems(items);
        Money total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.of(0), Money::add);
        return new Order(userId, items, total, OrderStatus.CREATED);
    }

    /**
     * 항목 컬렉션은 외부에서 변경할 수 없도록 불변 뷰로 노출한다.
     * (영속성 컨텍스트는 필드 접근으로 내부 리스트를 직접 다루므로 이 게터의 영향을 받지 않는다.)
     */
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
}
