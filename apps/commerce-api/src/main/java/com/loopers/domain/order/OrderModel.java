package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "orders")
@SQLRestriction("deleted_at IS NULL")
public class OrderModel extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    protected OrderModel() {}

    public OrderModel(Long memberId, Long totalPrice) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (totalPrice == null || totalPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 가격은 0 이상이어야 합니다.");
        }
        this.memberId = memberId;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 취소된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public Long getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }
}
