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

    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 1000;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    protected BrandModel() {}

    public BrandModel(String name, String description) {
        this.name = validateName(name);
        this.description = validateDescription(description);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 null이거나 공백일 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return name;
    }

    // description은 nullable (04 §2.2). 값이 있을 때만 길이 검증.
    private static String validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return description;
    }

    /** 활성 여부 — deletedAt이 null이면 활성 (01 §7.5, soft delete는 BaseEntity.delete()/restore()). */
    public boolean isActive() {
        return getDeletedAt() == null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
