package com.loopers.infrastructure.brand;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "brand")
@Getter
public class BrandJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    protected BrandJpaEntity() {}

    BrandJpaEntity(Long id, String name, String description, ZonedDateTime deletedAt) {
        if (id != null) {
            setId(id);
        }
        if (deletedAt != null) {
            setDeletedAt(deletedAt);
        }
        this.name = name;
        this.description = description;
    }
}
