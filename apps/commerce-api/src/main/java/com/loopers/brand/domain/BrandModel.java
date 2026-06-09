package com.loopers.brand.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "brand")
@SQLRestriction("deleted_at is null")
public class BrandModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    protected BrandModel() {}

    public BrandModel(String name, String description) {
        validate(name, description);
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        validate(name, description);
        this.name = name;
        this.description = description;
    }

    private static void validate(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.");
        }
    }
}
