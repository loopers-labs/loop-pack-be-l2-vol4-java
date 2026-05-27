package com.loopers.domain.brand.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "brands")
@SQLRestriction("deleted_at IS NULL")
@Getter
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected Brand() {}

    private Brand(String name) {
        validateName(name);
        this.name = name;
    }

    public static Brand create(String name) {
        return new Brand(name);
    }

    public void update(String name) {
        validateName(name);
        this.name = name;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (name.length() > 20) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 20자를 초과할 수 없습니다.");
        }
    }
}
