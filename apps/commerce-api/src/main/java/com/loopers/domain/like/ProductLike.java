package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(
    name = "product_like",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_product_like_user_product",
            columnNames = {"user_id", "product_id"}),
    indexes = @Index(name = "idx_product_like_product", columnList = "product_id"))
@Getter
public class ProductLike extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected ProductLike() {}

    public ProductLike(String userId, Long productId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 비어있을 수 없습니다.");
        }

        this.userId = userId;
        this.productId = productId;
    }
}
