package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "product_like", uniqueConstraints = {
    @UniqueConstraint(name = "uq_product_like_user_product", columnNames = {"user_id", "product_id"})
})
public class LikeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

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

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
