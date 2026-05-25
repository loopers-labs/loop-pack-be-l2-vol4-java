package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductModel extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final int IMAGE_URL_MAX_LENGTH = 500;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Column(name = "price", nullable = false)
    private Long price;

    @Embedded
    private Stock stock;

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        this.brandId = validateBrandId(brandId);
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.imageUrl = validateImageUrl(imageUrl);
        this.price = validatePrice(price);
        this.stock = new Stock(stock);
        this.likesCount = 0L;
    }

    // --- 검증 ---

    private static Long validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "brandId는 null일 수 없습니다.");
        }
        return brandId;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 null이거나 공백일 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return name;
    }

    // description은 nullable (04 §2.3). 값이 있을 때만 길이 검증.
    private static String validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return description;
    }

    // imageUrl은 nullable (04 §2.3). 값이 있을 때만 길이 검증.
    private static String validateImageUrl(String imageUrl) {
        if (imageUrl != null && imageUrl.length() > IMAGE_URL_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미지 URL은 " + IMAGE_URL_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return imageUrl;
    }

    private static Long validatePrice(Long price) {
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        return price;
    }

    // --- 도메인 메서드 ---

    /** 재고 차감. 불변식·부족 검사는 Stock VO에 위임 (04 §4.3). */
    public void deductStock(int quantity) {
        this.stock = this.stock.deduct(quantity);
    }

    /** 재고 복원 (결제 실패 시 원복 — 01 §7.6). */
    public void restoreStock(int quantity) {
        this.stock = this.stock.restore(quantity);
    }

    /** 좋아요 수 증가 (01 §7.3). */
    public void incrementLikesCount() {
        this.likesCount += 1;
    }

    /** 좋아요 수 감소. 음수 방지 — 0 미만으로 내려가지 않는다 (01 §7.3). */
    public void decrementLikesCount() {
        this.likesCount = Math.max(0L, this.likesCount - 1);
    }

    /** 활성 여부 — deletedAt이 null이면 활성 (soft delete는 BaseEntity.delete()/restore()). */
    public boolean isActive() {
        return getDeletedAt() == null;
    }

    public void update(String newName, String newDescription, String newImageUrl, Long newPrice, Integer newStock) {
        this.name = validateName(newName);
        this.description = validateDescription(newDescription);
        this.imageUrl = validateImageUrl(newImageUrl);
        this.price = validatePrice(newPrice);
        this.stock = new Stock(newStock);
    }

    // --- Getter ---

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Long getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock.getQuantity();
    }

    public Long getLikesCount() {
        return likesCount;
    }
}
