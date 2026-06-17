package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Getter
@Table(name = "product_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "product_id"}))
public class LikeModel extends BaseEntity {

    private Long memberId;
    private Long productId;

    protected LikeModel() {}

    public LikeModel(Long memberId, Long productId) {
        this.memberId = memberId;
        this.productId = productId;
    }
}