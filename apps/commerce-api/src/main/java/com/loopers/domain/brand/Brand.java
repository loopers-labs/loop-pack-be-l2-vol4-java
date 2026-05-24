package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.vo.BrandName;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private BrandName name;

    private String description;

    private Brand(String name, String description) {
        this.name = BrandName.of(name);
        this.description = description;
    }

    public static Brand create(String name, String description) {
        return new Brand(name, description);
    }

    public void update(String name, String description) {
        this.name = BrandName.of(name);
        this.description = description;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
