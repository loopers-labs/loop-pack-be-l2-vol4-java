package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductModel extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private Integer likeCount = 0;

    protected ProductModel() {}

    public ProductModel(String name, String description, Long price, Integer stock, Long brandId) {
        if (name == null || name.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        if (description == null || description.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        if (price == null || price < 0)
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        if (stock == null || stock < 0)
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        if (brandId == null)
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.brandId = brandId;
        this.likeCount = 0;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0)
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        if (this.stock < quantity)
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        this.stock -= quantity;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount <= 0)
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만이 될 수 없습니다.");
        this.likeCount--;
    }

    public void update(String name, String description, Long price, Integer stock) {
        if (name == null || name.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        if (description == null || description.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        if (price == null || price < 0)
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        if (stock == null || stock < 0)
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getPrice() { return price; }
    public Integer getStock() { return stock; }
    public Long getBrandId() { return brandId; }
    public Integer getLikeCount() { return likeCount; }
}
