package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "brands")
public class BrandJpaEntity extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    private BrandJpaEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static BrandJpaEntity of(String name, String description) {
        return new BrandJpaEntity(name, description);
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
