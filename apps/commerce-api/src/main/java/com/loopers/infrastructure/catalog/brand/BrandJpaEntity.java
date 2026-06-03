package com.loopers.infrastructure.catalog.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brand")
public class BrandJpaEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    protected BrandJpaEntity() {}

    private BrandJpaEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static BrandJpaEntity from(Brand brand) {
        return new BrandJpaEntity(brand.getName(), brand.getDescription());
    }

    public Brand toDomain() {
        return Brand.reconstruct(
            getId(),
            name,
            description,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(Brand brand) {
        this.name = brand.getName();
        this.description = brand.getDescription();
        if (brand.getDeletedAt() != null) {
            delete();
        } else {
            restore();
        }
    }
}
