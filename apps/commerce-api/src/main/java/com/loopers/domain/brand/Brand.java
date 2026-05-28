package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class Brand {

    private Long id;
    private String name;
    private String description;
    private boolean deleted;

    public Brand(String name, String description) {
        this(null, name, description, false);
    }

    private Brand(Long id, String name, String description, boolean deleted) {
        validateName(name);
        validateDescription(description);

        this.id = id;
        this.name = name;
        this.description = description;
        this.deleted = deleted;
    }

    public static Brand reconstruct(Long id, String name, String description, boolean deleted) {
        return new Brand(id, name, description, deleted);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVisible() {
        return !deleted;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void update(String newName, String newDescription) {
        validateName(newName);
        validateDescription(newDescription);

        this.name = newName;
        this.description = newDescription;
    }

    public void delete() {
        this.deleted = true;
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
