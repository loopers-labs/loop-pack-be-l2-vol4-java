package com.loopers.like.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(
    name = "product_like",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_product_like_member_product", columnNames = {"member_id", "product_id"}))
public class LikeModel extends BaseEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    protected LikeModel() {}

    public LikeModel(Long memberId, Long productId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 식별자는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 식별자는 필수입니다.");
        }
        this.memberId = memberId;
        this.productId = productId;
    }
}
