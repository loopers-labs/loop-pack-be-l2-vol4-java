package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class LikeEntity extends BaseEntity {

    private String userId;
    private String productId;

    protected LikeEntity() {}

    public LikeEntity(String userId, String productId) {
        validateUserId(userId);
        validateProductId(productId);
        this.userId = userId;
        this.productId = productId;
    }

    public static LikeEntity of(String id, String userId, String productId,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        LikeEntity entity = new LikeEntity();
        entity.userId = userId;
        entity.productId = productId;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public String getUserId() {
        return userId;
    }

    public String getProductId() {
        return productId;
    }

    private void validateUserId(String userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateProductId(String productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }
}
