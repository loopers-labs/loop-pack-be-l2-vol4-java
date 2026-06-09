package com.loopers.product.domain;

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
@Table(name = "product")
@SQLRestriction("deleted_at is null")
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false, updatable = false)
    private Long brandId;

    private String name;
    private String description;
    private Long price;
    private Integer stock;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, Long price, Integer stock) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 필수입니다.");
        }
        validate(name, description, price, stock);

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public void update(String newName, String newDescription, Long newPrice, Integer newStock) {
        validate(newName, newDescription, newPrice, newStock);

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
        this.stock = newStock;
    }

    /** 재고를 차감한다. 음수가 될 수 없으며, 재고가 부족하면 예외를 던진다. */
    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stock < quantity) {
            throw new CoreException(
                ErrorType.CONFLICT,
                "[id = " + getId() + "] 재고가 부족합니다. (보유: " + this.stock + ", 요청: " + quantity + ")");
        }
        this.stock -= quantity;
    }

    /** 차감된 재고를 복구한다. */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 수량은 1 이상이어야 합니다.");
        }
        this.stock += quantity;
    }

    private static void validate(String name, String description, Long price, Integer stock) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (stock == null || stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}
