package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLineModel> orderLines = new ArrayList<>();

    protected OrderModel() {}

    public OrderModel(String userLoginId, List<OrderLineModel> orderLines) {
        validateUserLoginId(userLoginId);
        validateOrderLines(orderLines);

        this.userLoginId = userLoginId;
        this.status = OrderStatus.CREATED;
        this.orderLines.addAll(orderLines);
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderLineModel> getOrderLines() {
        return Collections.unmodifiableList(orderLines);
    }

    public Long getTotalAmount() {
        return orderLines.stream()
            .mapToLong(OrderLineModel::getAmount)
            .sum();
    }

    private void validateUserLoginId(String userLoginId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 로그인 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateOrderLines(List<OrderLineModel> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }
    }
}
