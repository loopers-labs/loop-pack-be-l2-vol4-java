package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brand")
public class BrandModel extends BaseEntity {

    private String name;
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    protected BrandModel() {}

    public BrandModel(String name, String description, String logoUrl) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }

        this.name = name;
        this.description = description;
        this.logoUrl = logoUrl;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void update(String newName, String newDescription, String newLogoUrl) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }

        this.name = newName;
        this.description = newDescription;
        this.logoUrl = newLogoUrl;
    }
}
