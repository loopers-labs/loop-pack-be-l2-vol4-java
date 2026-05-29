package com.loopers.domain.wishlist;

import com.loopers.domain.BaseEntity;
import com.loopers.support.Guard;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlists", uniqueConstraints = {
        @UniqueConstraint(name = "uq_wishlist_user_product", columnNames = {"user_id", "product_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    public WishlistModel(Long userId, Long productId) {
        Guard.notNull(userId, "사용자 ID는 필수입니다.");
        Guard.notNull(productId, "상품 ID는 필수입니다.");
        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() { return userId; }

    public Long getProductId() { return productId; }
}
