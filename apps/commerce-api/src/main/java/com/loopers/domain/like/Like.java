package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Like {

    private final Long userId;

    private final Long productId;

    private Like(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.productId = productId;
    }

    public static Like create(Long userId, Long productId) {
        return new Like(userId, productId);
    }

    public static Like restore(Long userId, Long productId) {
        return new Like(userId, productId);
    }
}
