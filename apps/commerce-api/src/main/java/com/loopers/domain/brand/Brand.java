package com.loopers.domain.brand;


import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    private Brand(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
    }

    public static Brand create(String name, String description) {
        return new Brand(name, description);
    }

    public void modify(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 명은 비어있을 수 없습니다.");
        }
    }
}
