package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
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

    protected BrandJpaEntity() {
    }

    private BrandJpaEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static BrandJpaEntity from(Brand brand) {
        return new BrandJpaEntity(brand.getName(), brand.getDescription());
    }

    public Brand toDomain() {
        return Brand.reconstruct(getId(), name, description, getDeletedAt() != null);
    }

    public void update(Brand brand) {
        this.name = brand.getName();
        this.description = brand.getDescription();
        if (brand.isDeleted()) {
            delete();
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
