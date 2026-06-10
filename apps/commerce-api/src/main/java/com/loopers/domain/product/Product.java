package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "products", indexes = @Index(name = "idx_products_like_count", columnList = "like_count desc, id desc"))
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Embedded
    @AttributeOverride(name = "quantity", column = @Column(name = "stock_quantity", nullable = false))
    private Stock stock;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    private Product(Long brandId, String name, Money price, Stock stock) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        validateStock(stock);
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public static Product create(Long brandId, String name, Money price, Stock stock) {
        return new Product(brandId, name, price, stock);
    }

    public void modify(String name, Money price) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.price = price;
    }

    public void decreaseStock(int qty) {
        if (!stock.hasAtLeast(qty)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 재고가 부족합니다.");
        }
        this.stock = stock.decrease(qty);
    }


    public boolean isSoldOut() {
        return this.stock.isSoldOut();
    }

    public void adjustStock(int newQuantity) {
        this.stock = this.stock.adjust(newQuantity);
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Money price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        }
    }

    private void validateStock(Stock stock) {
        if (stock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 비어있을 수 없습니다.");
        }
    }
}
