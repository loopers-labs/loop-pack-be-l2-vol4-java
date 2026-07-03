package com.loopers.metrics.ssot;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좋아요 재계산의 SSOT — commerce-api likes 테이블의 read-only 뷰.
 * deleted_at(soft delete)은 BaseEntity 가 제공하며, 재계산은 deleted_at IS NULL 만 센다.
 */
@Entity
@Table(name = "likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Like extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
