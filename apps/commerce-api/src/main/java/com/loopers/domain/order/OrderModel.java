package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = 0;
    }

    /**
     * 주문 항목을 추가하고 totalAmount를 갱신한다.
     */
    public void addItem(OrderItemModel item) {
        this.items.add(item);
        this.totalAmount += item.totalPrice();
    }

    public List<OrderItemModel> getItems() {
        return Collections.unmodifiableList(items);
    }
}
