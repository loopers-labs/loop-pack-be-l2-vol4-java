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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    protected BrandModel() {}

    public BrandModel(String name, String description) {
        validateName(name);
        validateDescription(description);

        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVisible() {
        return getDeletedAt() == null;
    }

    public void update(String newName, String newDescription) {
        validateName(newName);
        validateDescription(newDescription);

        this.name = newName;
        this.description = newDescription;
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
}
