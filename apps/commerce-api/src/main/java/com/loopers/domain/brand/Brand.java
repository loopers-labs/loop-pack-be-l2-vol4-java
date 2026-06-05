package com.loopers.domain.brand;

import com.loopers.domain.BaseDomain;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class Brand extends BaseDomain {

    private String name;
    private String description;

    public Brand(String name, String description) {
        validate(name, description);
        this.name = name;
        this.description = description;
    }

    public Brand(Long id, String name, String description,
                 ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public void update(String newName, String newDescription) {
        validate(newName, newDescription);
        this.name = newName;
        this.description = newDescription;
    }

    private void validate(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (name.length() > 20) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 20자 이하여야 합니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.");
        }
    }
}
