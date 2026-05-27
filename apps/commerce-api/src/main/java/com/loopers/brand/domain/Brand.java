package com.loopers.brand.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
        validate();
    }

    public static Brand create(String name, String description) {
        return new Brand(name, description);
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        validate();
    }

    private void validate() {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.");
        }
    }
}
