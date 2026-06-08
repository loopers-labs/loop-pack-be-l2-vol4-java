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
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "product")
@SQLRestriction("deleted_at IS NULL")
public class ProductModel extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 100;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "stock_quantity", nullable = false))
    private Quantity stockQuantity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, Money price, Quantity stockQuantity, String imageUrl) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 " + NAME_MAX_LENGTH + "자 이내여야 합니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (stockQuantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 필수입니다.");
        }

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
        this.likeCount = 0;
    }

    /**
     * 재고를 차감한다. 부족하면 BAD_REQUEST.
     * Quantity 의 minus 가 음수를 막아주지만, 도메인 의미상 명확한 예외를 던지기 위해 한 번 더 검증한다.
     */
    public void decreaseStock(Quantity amount) {
        if (!this.stockQuantity.isGreaterThanOrEqual(amount)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "재고가 부족합니다. 현재 재고: " + this.stockQuantity.value() + ", 요청 수량: " + amount.value());
        }
        this.stockQuantity = this.stockQuantity.minus(amount);
    }

    /**
     * 재고를 복원한다. (주문 실패 등으로 차감된 수량을 되돌릴 때)
     */
    public void restoreStock(Quantity amount) {
        this.stockQuantity = this.stockQuantity.plus(amount);
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    /**
     * 노출용 가용성. 재고가 1 이상이면 true.
     */
    public boolean isAvailable() {
        return this.stockQuantity.isPositive();
    }

    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getPrice() { return price; }
    public Quantity getStockQuantity() { return stockQuantity; }
    public String getImageUrl() { return imageUrl; }
    public int getLikeCount() { return likeCount; }
}
