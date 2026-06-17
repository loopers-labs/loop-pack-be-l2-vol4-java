package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "likes")
public class Like extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    protected Like() {}

    public Like(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
}
