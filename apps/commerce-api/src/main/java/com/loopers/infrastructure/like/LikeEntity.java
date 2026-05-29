package com.loopers.infrastructure.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.like.LikeModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "likes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class LikeEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    private LikeEntity(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static LikeEntity from(LikeModel model) {
        return new LikeEntity(model.getUserId(), model.getProductId());
    }

    public LikeModel toDomain() {
        return new LikeModel(
            getId(),
            userId,
            productId,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
