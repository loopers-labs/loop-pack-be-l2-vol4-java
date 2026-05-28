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

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    protected BrandModel() {}

    public BrandModel(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        this.name = name;
        this.description = description;
    }

    public void changeName(String name) {
        requireNotDeleted();
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        this.name = name;
    }

    public void changeDescription(String description) {
        requireNotDeleted();
        this.description = description;
    }

    private void requireNotDeleted() {
        if (getDeletedAt() != null) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND,
                "[id = " + getId() + "] 삭제된 브랜드는 변경할 수 없습니다.");
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
