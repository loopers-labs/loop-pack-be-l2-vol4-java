package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity(name = "Brand")
@Table(name = "brand")
public class BrandEntity extends BaseEntity {

    private String name;
    private String description;

    protected BrandEntity() {}

    public BrandEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Brand toDomain() {
        return new Brand(getId(), name, description, getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(Brand domain) {
        this.name = domain.getName();
        this.description = domain.getDescription();
        if (domain.getDeletedAt() != null) {
            delete();
        } else {
            restore();
        }
    }
}
