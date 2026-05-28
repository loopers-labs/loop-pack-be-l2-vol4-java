package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.ProductModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
    name = "likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    /** 조회 전용 연관 — product_id 컬럼을 공유, insert/update는 productId UUID 필드가 담당 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private ProductModel product;

    public LikeModel(UUID userId, UUID productId) {
        this.userId = userId;
        this.productId = productId;
    }
}
