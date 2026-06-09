package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Table(name = "brand")
public class BrandModel extends BaseEntity {

    private String name;
    private String description;
    private String imageUrl;
    private ZonedDateTime suspendedAt;

    protected BrandModel() {}

    public BrandModel(String name, String description, String imageUrl) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    public void suspend() {
        if (suspendedAt == null) {
            suspendedAt = ZonedDateTime.now();
        }
    }

    public void reinstate() {
        suspendedAt = null;
    }

    public void update(String name, String description, String imageUrl) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }
}
