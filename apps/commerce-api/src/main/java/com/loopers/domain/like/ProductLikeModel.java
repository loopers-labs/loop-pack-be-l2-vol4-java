package com.loopers.domain.like;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product_likes",
        uniqueConstraints = {@UniqueConstraint(name = "uk_product_likes_user_product", columnNames = {"product_id", "user_id"})},
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id")
        })
public class ProductLikeModel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    public ProductLikeModel(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }
}
