package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    protected Brand() {}

    public Brand(String name) {
        validateName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void updateName(String newName) {
        validateName(newName);
        this.name = newName;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
    }
}
