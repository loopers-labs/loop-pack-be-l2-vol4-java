package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Table(name = "product")
public class ProductModel extends BaseEntity {

    private Long brandId;
    private String name;
    private String description;
    private Long price;
    private Integer stock;
    private String imageUrl;
    private ZonedDateTime soldOutAt;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, Long price, Integer stock, String imageUrl) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
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
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
    }

    public boolean isSoldOut() {
        return soldOutAt != null;
    }

    public void deductStock(int quantity) {
        if (stock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "재고가 부족합니다. [현재 재고 = " + stock + ", 요청 수량 = " + quantity + "]");
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.soldOutAt = ZonedDateTime.now();
        }
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
        if (this.stock > 0) {
            this.soldOutAt = null;
        }
    }

    public void update(String newName, String newDescription, Long newPrice, Integer newStock, String newImageUrl) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null || newPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (newStock == null || newStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
        this.stock = newStock;
        this.imageUrl = newImageUrl;
    }
}
