package com.loopers.domain.catalog.like;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class ProductLike extends DomainEntity {

    private String userId;

    private Long productId;

    public ProductLike(String userId, Long productId) {
        validateUserId(userId);
        validateProductId(productId);

        this.userId = userId;
        this.productId = productId;
    }

    public static ProductLike reconstruct(
        Long id,
        String userId,
        Long productId,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        ProductLike productLike = new ProductLike(userId, productId);
        productLike.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return productLike;
    }

    public String getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    private void validateUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateProductId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }
}
