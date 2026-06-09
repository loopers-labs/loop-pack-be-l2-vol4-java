package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    protected OrderModel() {}

    private OrderModel(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 필수입니다.");
        }
        this.memberId = memberId;
    }

    public static OrderModel create(Long memberId) {
        return new OrderModel(memberId);
    }

    /** 주문 항목을 추가한다. 내부 OrderItem 생성은 Aggregate Root 를 통해서만 이루어진다. */
    public void addItem(OrderItemSnapshot snapshot, int quantity) {
        OrderItem item = new OrderItem(this, snapshot, quantity);
        this.items.add(item);
        this.totalAmount += item.getLineAmount();
    }

    public boolean belongsTo(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
