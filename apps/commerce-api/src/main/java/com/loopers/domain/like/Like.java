package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(
    name = "likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
public class Like extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected Like() {}

    public Like(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public boolean isSameUser(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
}
