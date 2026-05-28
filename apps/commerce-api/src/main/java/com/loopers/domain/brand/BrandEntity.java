package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class BrandEntity extends BaseEntity {

    private String name;
    private String description;

    protected BrandEntity() {}

    public BrandEntity(String name, String description) {
        validateName(name);
        validateDescription(description);
        this.name = name;
        this.description = description;
    }

    public static BrandEntity of(Long id, String name, String description,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        BrandEntity model = new BrandEntity();
        model.name = name;
        model.description = description;
        model.reconstruct(id, createdAt, updatedAt, deletedAt);
        return model;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
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
        if (!name.equals(name.strip())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 앞뒤에 공백을 포함할 수 없습니다.");
        }
        if (name.length() > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 100자를 초과할 수 없습니다.");
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.");
        }
        if (description.length() > 500) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 500자를 초과할 수 없습니다.");
        }
    }
}
