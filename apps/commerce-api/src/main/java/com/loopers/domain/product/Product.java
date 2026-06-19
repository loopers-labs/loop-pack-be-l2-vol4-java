package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 엔티티.
 * 재고를 스스로 관리하며(주문 시 차감), 재고가 음수가 되지 않도록 도메인 레벨에서 보장한다.
 * 브랜드는 식별자(brandId)로만 참조하여 애그리거트 경계를 분리한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    private String name;

    private String description;

    @Column(name = "price", nullable = false)
    private Money price;

    private Integer stock;

    private Long brandId;

    @Builder(access = AccessLevel.PRIVATE)
    private Product(String name, String description, Money price, Integer stock, Long brandId) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        }
        if (stock == null || stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품은 브랜드에 속해야 합니다.");
        }

        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.brandId = brandId;
    }

    public static Product create(String name, String description, Money price, Integer stock, Long brandId) {
        return Product.builder()
            .name(name)
            .description(description)
            .price(price)
            .stock(stock)
            .brandId(brandId)
            .build();
    }

    public void update(String newName, String newDescription, Money newPrice, Integer newStock) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        }
        if (newStock == null || newStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
        this.stock = newStock;
    }

    /**
     * 주어진 수량만큼 주문 가능한지(재고가 충분한지) 판단한다.
     */
    public boolean isOrderable(int quantity) {
        return quantity > 0 && this.stock >= quantity;
    }

    /**
     * 재고를 차감한다. 재고가 부족하면 예외를 던져 음수 재고를 방지한다.
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다. [현재 재고 = " + this.stock + ", 요청 = " + quantity + "]");
        }
        this.stock -= quantity;
    }

}
