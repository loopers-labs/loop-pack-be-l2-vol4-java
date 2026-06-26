package com.loopers.infrastructure.like;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(name = "unique_likes_user_product", columnNames = {"ref_user_id", "ref_product_id"}),
        indexes = {
            // 사용자 좋아요 목록 페이지네이션
            @Index(name = "idx_likes_user_id_deleted_at",    columnList = "ref_user_id, deleted_at"),
            // 상품 삭제 시 좋아요 일괄 soft delete
            @Index(name = "idx_likes_product_id_deleted_at", columnList = "ref_product_id, deleted_at")
        }
)
@Getter
public class LikeJpaEntity extends BaseJpaEntity {

    @Column(name = "ref_user_id", nullable = false)
    private String userId;

    @Column(name = "ref_product_id", nullable = false)
    private String productId;

    protected LikeJpaEntity() {}

    @Override
    protected String idCode() {
        return "LIK";
    }

    LikeJpaEntity(String id, String userId, String productId, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.userId = userId;
        this.productId = productId;
    }
}
