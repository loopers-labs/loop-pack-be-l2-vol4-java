package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class BrandModel extends BaseEntity {

    private String name;
    private String description;

    protected BrandModel() {}

    public BrandModel(String name, String description) {
        validateName(name);
        validateDescription(description);
        this.name = name;
        this.description = description;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.");
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public void update(String name, String description) {
        validateName(name);
        validateDescription(description);
        this.name = name;
        this.description = description;
    }
}
