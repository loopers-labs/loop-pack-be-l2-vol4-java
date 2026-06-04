package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Product {

    private final Long id;

    private final Long brandId;

    private String name;

    private Money price;

    private Stock stock;

    private boolean deleted;

    private Product(Long id, Long brandId, String name, Money price, Stock stock, boolean deleted) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        validateStock(stock);
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.deleted = deleted;
    }

    public static Product create(Long brandId, String name, Money price, Stock stock) {
        return new Product(null, brandId, name, price, stock, false);
    }

    public static Product restore(Long id, Long brandId, String name, Money price, Stock stock) {
        if (id == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        return new Product(id, brandId, name, price, stock, false);
    }

    public void modify(String name, Money price) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.price = price;
    }

    public boolean hasEnoughStock(int qty) {
        return this.stock.hasAtLeast(qty);
    }

    public boolean isSoldOut() {
        return this.stock.isSoldOut();
    }

    public void adjustStock(int newQuantity) {
        this.stock = this.stock.adjust(newQuantity);
    }

    public void decreaseStock(int qty) {
        this.stock = this.stock.decrease(qty);
    }

    public void delete() {
        this.deleted = true;
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
