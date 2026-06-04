package com.loopers.infrastructure.like;

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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_likes")
@IdClass(LikeJpaEntity.LikeId.class)
public class LikeJpaEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    private LikeJpaEntity(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static LikeJpaEntity of(Long userId, Long productId) {
        return new LikeJpaEntity(userId, productId);
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
