package com.loopers.like.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
public class LikeModel extends BaseEntity {

    private Long userId;
    private Long productId;

    protected LikeModel() {}

    public LikeModel(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 비어있을 수 없습니다.");
        }

        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
}
