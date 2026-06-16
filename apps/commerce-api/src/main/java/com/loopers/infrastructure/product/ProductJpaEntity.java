package com.loopers.infrastructure.product;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product")
@Getter
public class ProductJpaEntity extends BaseJpaEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    protected ProductJpaEntity() {}

    ProductJpaEntity(Long id, Long brandId, String name, String description, Long price, Long likeCount, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.likeCount = likeCount;
    }
}
