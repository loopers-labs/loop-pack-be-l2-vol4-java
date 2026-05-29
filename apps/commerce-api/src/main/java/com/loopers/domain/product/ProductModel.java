package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    private String name;
    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "stock", nullable = false))
    private Quantity stock;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, Long price, Integer stock) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (stock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 필수입니다.");
        }

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = Money.of(price);      // 음수 검증은 Money 에 위임
        this.stock = Quantity.of(stock);   // 음수 검증은 Quantity 에 위임
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price.getAmount();
    }

    public Integer getStock() {
        return stock.getValue();
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void decreaseStock(Quantity quantity) {
        if (quantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 필수입니다.");
        }
        if (!stock.isGreaterThanOrEqual(quantity)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.stock = this.stock.minus(quantity);
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void update(String newName, String newDescription, Long newPrice, Integer newStock) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (newStock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 필수입니다.");
        }

        this.name = newName;
        this.description = newDescription;
        this.price = Money.of(newPrice);
        this.stock = Quantity.of(newStock);
    }
}
