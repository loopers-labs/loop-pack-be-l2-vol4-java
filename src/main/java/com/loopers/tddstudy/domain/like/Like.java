package com.loopers.tddstudy.domain.like;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    private LocalDateTime createdAt;

    protected Like() {}

    public Like(Long userId, Long productId) {
        validateUserId(userId);
        validateProductId(productId);
        this.userId = userId;
        this.productId = productId;
        this.createdAt = LocalDateTime.now();
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("유저 ID는 필수입니다.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
