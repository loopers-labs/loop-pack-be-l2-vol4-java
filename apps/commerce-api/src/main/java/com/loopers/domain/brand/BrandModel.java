package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "brand")
@SQLDelete(sql = "UPDATE brand SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class BrandModel extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    protected BrandModel() {}

    public BrandModel(String name, String description, String imageUrl) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public void update(String name, String description, String imageUrl) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
}
