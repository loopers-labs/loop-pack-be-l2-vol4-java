package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductModel extends BaseEntity {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_IMAGE_URL_LENGTH = 500;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    private String name;
    private String description;
    private Long price;
    private Integer stock;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    protected ProductModel() {}

    public ProductModel(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {
        validateBrandId(brandId);
        validateName(name);
        validateDescription(description);
        validatePrice(price);
        validateStock(stock);
        validateImageUrl(imageUrl);

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.likeCount = 0L;
        this.status = stock > 0 ? ProductStatus.ACTIVE : ProductStatus.SOLD_OUT;
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
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public ProductStatus getStatus() {
        return status;
    }

    /**
     * 재고를 차감한다. 음수 방지는 도메인 레벨에서 보장한다.
     * 재고가 0 이 되면 자동으로 SOLD_OUT 상태로 전이한다.
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다. (현재 재고: " + this.stock + ", 요청: " + quantity + ")");
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    /**
     * 재고를 증가시킨다. 보상 트랜잭션(주문 취소 등)에서 사용한다.
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가 수량은 1 이상이어야 합니다.");
        }
        this.stock += quantity;
        if (this.status == ProductStatus.SOLD_OUT && this.stock > 0) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    /**
     * 좋아요 카운트를 1 증가시킨다.
     * 동시성 환경에서 lost update 가능성이 있음 — 인프라 레벨 atomic UPDATE 와 함께 사용해야 한다.
     */
    public void incrementLikeCount() {
        this.likeCount += 1;
    }

    /**
     * 좋아요 카운트를 1 감소시킨다. 0 미만으로 떨어지지 않는다.
     */
    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount -= 1;
        }
    }

    private static void validateBrandId(Long brandId) {
        if (brandId == null || brandId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 양수여야 합니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 " + MAX_NAME_LENGTH + "자를 초과할 수 없습니다.");
        }
    }

    private static void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 " + MAX_DESCRIPTION_LENGTH + "자를 초과할 수 없습니다.");
        }
    }

    private static void validatePrice(Long price) {
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    private static void validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }

    private static void validateImageUrl(String imageUrl) {
        if (imageUrl != null && imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미지 URL 은 " + MAX_IMAGE_URL_LENGTH + "자를 초과할 수 없습니다.");
        }
    }
}
