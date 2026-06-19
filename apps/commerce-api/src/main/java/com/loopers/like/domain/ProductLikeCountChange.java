package com.loopers.like.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_like_count_change")
public class ProductLikeCountChange extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "change_amount", nullable = false)
    private int changeAmount;

    private ProductLikeCountChange(Long productId, int changeAmount) {
        this.productId = productId;
        this.changeAmount = changeAmount;
    }

    public static ProductLikeCountChange from(LikeChange change) {
        if (!change.hasCountChange()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수 변경이 없습니다.");
        }
        return new ProductLikeCountChange(change.productId(), change.countChangeAmount());
    }

    @Override
    protected void guard() {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        if (changeAmount != 1 && changeAmount != -1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수 변경값은 1 또는 -1이어야 합니다.");
        }
    }
}
