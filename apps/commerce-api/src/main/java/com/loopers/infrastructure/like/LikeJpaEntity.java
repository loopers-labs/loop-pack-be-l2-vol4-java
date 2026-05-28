package com.loopers.infrastructure.like;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
@Getter
public class LikeJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected LikeJpaEntity() {}

    LikeJpaEntity(Long id, Long userId, Long productId, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.userId = userId;
        this.productId = productId;
    }
}
