package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "product")
@SQLRestriction("deleted_at IS NULL")
public class ProductModel extends BaseEntity {

    private Long brandId;
    private String name;
    private BigDecimal price;
    private Long likeCount;

    protected ProductModel() {
    }

    public ProductModel(Long brandId, String name, BigDecimal price) {
        validate(name, price);

        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.likeCount = 0L;
    }

    public void update(String newName, BigDecimal newPrice) {
        validate(newName, newPrice);

        this.name = newName;
        this.price = newPrice;
    }

    private void validate(String name, BigDecimal price) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
