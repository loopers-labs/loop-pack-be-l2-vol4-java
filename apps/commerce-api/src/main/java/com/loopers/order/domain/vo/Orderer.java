package com.loopers.order.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Orderer(
    @Column(name = "user_id", nullable = false)
    Long userId
) {

    public Orderer {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자 ID는 비어있을 수 없습니다.");
        }
    }

    public static Orderer of(Long userId) {
        return new Orderer(userId);
    }

    public boolean isSameUser(Long userId) {
        return this.userId.equals(userId);
    }
}
