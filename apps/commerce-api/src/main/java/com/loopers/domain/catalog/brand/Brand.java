package com.loopers.domain.catalog.brand;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class Brand extends DomainEntity {

    private String name;

    private String description;

    public Brand(String name, String description) {
        validateName(name);
        validateDescription(description);

        this.name = name;
        this.description = description;
    }

    public static Brand reconstruct(
        Long id,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        Brand brand = new Brand(name, description);
        brand.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return brand;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return getDeletedAt() == null;
    }

    public void update(String name, String description) {
        ensureActive();
        validateName(name);
        validateDescription(description);

        this.name = name;
        this.description = description;
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 브랜드는 변경할 수 없습니다.");
        }
    }

    private void validateName(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
    }

    private void validateDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.");
        }
    }
}
