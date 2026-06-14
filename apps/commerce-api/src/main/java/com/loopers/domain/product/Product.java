package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Embedded
    private Money price;

    @Embedded
    private Stock stock;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private Long brandId;

    public Product(String name, String description, Money price, Stock stock, Long brandId) {
        validate(name);
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.brandId = brandId;
    }

    private static void validate(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    public void decreaseStock(Quantity quantity) {
        this.stock = this.stock.decrease(quantity);
    }
}
