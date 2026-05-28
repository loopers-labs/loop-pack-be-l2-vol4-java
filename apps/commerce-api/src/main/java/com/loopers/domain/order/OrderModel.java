package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    private Long memberId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Long totalAmount;

    protected OrderModel() {}

    public OrderModel(Long memberId, Long totalAmount) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (totalAmount == null || totalAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총금액은 0 이상이어야 합니다.");
        }
        this.memberId = memberId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태의 주문만 확정할 수 있습니다.");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태의 주문만 취소할 수 있습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
