package com.loopers.domain.order.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "orders")
@Getter
public class Order extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    protected Order() {}

    private Order(Long memberId, Long totalAmount) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 0보다 커야 합니다.");
        }
        this.memberId = memberId;
        this.totalAmount = totalAmount;
    }

    public static Order create(Long memberId, Long totalAmount) {
        return new Order(memberId, totalAmount);
    }
}
