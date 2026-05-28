package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "brands")
@SQLRestriction("deleted_at IS NULL")
public class BrandModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected BrandModel() {}

    public BrandModel(String name) {
        validateName(name);
        this.name = name;
    }

    public void update(String name) {
        validateName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 비어있을 수 없습니다.");
        }
    }
}
