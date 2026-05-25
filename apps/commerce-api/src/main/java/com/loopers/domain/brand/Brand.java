package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "brand")
public class Brand extends BaseEntity {

    private String name;
    private String description;

    protected Brand() {}

    public Brand(String name, String description) {
        validate(name, description);
        this.name = name;
        this.description = description;
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
