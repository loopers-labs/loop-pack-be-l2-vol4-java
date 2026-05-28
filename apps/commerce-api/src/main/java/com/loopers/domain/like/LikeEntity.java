package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class LikeEntity extends BaseEntity {

    private Long userId;
    private Long productId;

    protected LikeEntity() {}

    public LikeEntity(Long userId, Long productId) {
        validateUserId(userId);
        validateProductId(productId);
        this.userId = userId;
        this.productId = productId;
    }

    public static LikeEntity of(Long id, Long userId, Long productId,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        LikeEntity entity = new LikeEntity();
        entity.userId = userId;
        entity.productId = productId;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }
}
