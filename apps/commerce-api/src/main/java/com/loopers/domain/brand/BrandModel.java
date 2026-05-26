package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "brands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandModel extends BaseEntity {

    @Embedded
    private BrandName name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder
    private BrandModel(String rawName, String rawDescription) {
        this.name = BrandName.from(rawName);
        this.description = rawDescription;
    }

    public void update(String rawName, String rawDescription) {
        this.name = BrandName.from(rawName);
        this.description = rawDescription;
    }
}
