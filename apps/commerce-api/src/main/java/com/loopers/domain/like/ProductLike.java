package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class ProductLike {

    private Long userId;
    private Long productId;
    private ZonedDateTime createdAt;

    public ProductLike(Long userId, Long productId) {
        validate(userId, productId);
        this.userId = userId;
        this.productId = productId;
        this.createdAt = ZonedDateTime.now();
    }

    public ProductLike(Long userId, Long productId, ZonedDateTime createdAt) {
        this.userId = userId;
        this.productId = productId;
        this.createdAt = createdAt;
    }

    private void validate(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }
}
