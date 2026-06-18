package com.loopers.domain.product;

import com.loopers.domain.BaseSoftDeleteEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE product SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(
        name = "product",
        indexes = {
                @Index(name = "idx_brand_id", columnList = "brand_id")
        }
)
public class ProductModel extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal price;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL)
    private StockModel stock;

    @Column(nullable = false, columnDefinition = "int default 0")
    private int likeCount = 0;

    @Builder
    public ProductModel(Long brandId, String name, BigDecimal price) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);

        this.brandId = brandId;
        this.name = name;
        this.price = price;
    }

    public void update(String name, BigDecimal price) {
        validateName(name);
        validatePrice(price);

        this.name = name;
        this.price = price;
    }

    public void assignStock(int quantity) {
        this.stock = new StockModel(this, quantity);
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0보다 작을 수 없습니다.");
        }
        this.likeCount--;
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
