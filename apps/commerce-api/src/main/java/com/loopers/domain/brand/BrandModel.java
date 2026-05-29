package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.enums.BrandStatus;
import com.loopers.support.Guard;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands", uniqueConstraints = {
        @UniqueConstraint(name = "uq_brand_name", columnNames = {"name"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandModel extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrandStatus status;

    public BrandModel(String name) {
        validateName(name);
        this.name = name;
        this.status = BrandStatus.ACTIVE;
    }

    public void update(String name) {
        validateName(name);
        this.name = name;
    }

    @Override
    public void delete() {
        super.delete();
        this.status = BrandStatus.INACTIVE;
    }

    private void validateName(String name) {
        Guard.notBlank(name, "브랜드 이름은 비어있을 수 없습니다.");
        Guard.minLength(name, 2, "브랜드 이름은 2글자 이상이어야 합니다.");
    }

    public String getName() { return name; }

    public BrandStatus getStatus() { return status; }
}
