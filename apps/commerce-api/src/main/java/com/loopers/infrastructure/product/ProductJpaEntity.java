package com.loopers.infrastructure.product;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "product",
    indexes = {
        // 전체 조회 + price 정렬 / COUNT(*) 커버
        @Index(name = "idx_product_deleted_at_price",            columnList = "deleted_at, price"),
        // 전체 조회 + like_count 정렬
        @Index(name = "idx_product_deleted_at_like_count",       columnList = "deleted_at, like_count"),
        // 브랜드 필터 + price 정렬 (ref_brand_id 선두 - 선택도 높음)
        @Index(name = "idx_product_brand_deleted_at_price",      columnList = "ref_brand_id, deleted_at, price"),
        // 브랜드 필터 + like_count 정렬
        @Index(name = "idx_product_brand_deleted_at_like_count", columnList = "ref_brand_id, deleted_at, like_count"),
        // 브랜드 필터 + latest(id) 정렬 — InnoDB가 PK(id)를 묵시적으로 포함하므로 id 명시 불필요
        @Index(name = "idx_product_brand_deleted_at",            columnList = "ref_brand_id, deleted_at")
    }
)
@Getter
public class ProductJpaEntity extends BaseJpaEntity {

    @Column(name = "ref_brand_id", nullable = false)
    private String brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    protected ProductJpaEntity() {}

    @Override
    protected String idCode() {
        return "PRD";
    }

    ProductJpaEntity(String id, String brandId, String name, String description, Long price, Long likeCount, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.likeCount = likeCount;
    }
}
