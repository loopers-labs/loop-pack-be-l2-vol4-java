package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Getter
@Entity(name = "ProductLike")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_likes")
@IdClass(Like.LikeId.class)
public class Like {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    private Like(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.productId = productId;
    }

    public static Like create(Long userId, Long productId) {
        return new Like(userId, productId);
    }

    @PrePersist
    private void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = ZonedDateTime.now();
        }
    }

    @NoArgsConstructor
    @EqualsAndHashCode
    public static class LikeId implements Serializable {
        private Long userId;
        private Long productId;

        public LikeId(Long userId, Long productId) {
            this.userId = Objects.requireNonNull(userId);
            this.productId = Objects.requireNonNull(productId);
        }
    }
}
