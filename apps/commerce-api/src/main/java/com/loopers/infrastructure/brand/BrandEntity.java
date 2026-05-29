package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.BrandModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "brand")
public class BrandEntity extends BaseEntity {

    private String name;
    private String description;

    private BrandEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static BrandEntity from(BrandModel model) {
        return new BrandEntity(model.getName(), model.getDescription());
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public BrandModel toDomain() {
        return new BrandModel(
            getId(),
            name,
            description,
            getCreatedAt(),
            getUpdatedAt()
        );
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
}
