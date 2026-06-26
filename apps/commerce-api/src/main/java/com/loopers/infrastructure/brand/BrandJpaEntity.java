package com.loopers.infrastructure.brand;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "brand",
    indexes = {
        @Index(name = "idx_brand_deleted_at", columnList = "deleted_at")
    }
)
@Getter
public class BrandJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    protected BrandJpaEntity() {}

    @Override
    protected String idCode() {
        return "BRD";
    }

    BrandJpaEntity(String id, String name, String description, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.name = name;
        this.description = description;
    }
}
