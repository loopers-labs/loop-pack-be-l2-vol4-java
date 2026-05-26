package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * Product Aggregate 루트 — 순수 도메인 객체. 재고/좋아요 수/활성 상태 같은 비즈니스 규칙만 보유하고
 * 영속 기술(JPA)에는 의존하지 않는다. JPA 매핑은 infrastructure.product.ProductEntity가 담당하고,
 * 도메인 ↔ 엔티티 변환은 ProductEntityMapper가 처리한다.
 *
 * Stock은 행동 VO로 도메인 내부에서만 사용하고, 영속 시 엔티티에서 Integer 컬럼으로 풀린다.
 */
public class ProductModel {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final int IMAGE_URL_MAX_LENGTH = 500;

    private final Long id;   // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private final Long brandId;
    private String name;
    private String description;
    private String imageUrl;
    private Long price;
    private Stock stock;
    private Long likesCount;
    private ZonedDateTime deletedAt;   // null이면 활성 (soft delete, 01 §7.5)

    public ProductModel(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        this.id = null;
        this.brandId = validateBrandId(brandId);
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.imageUrl = validateImageUrl(imageUrl);
        this.price = validatePrice(price);
        this.stock = new Stock(stock);
        this.likesCount = 0L;
        this.deletedAt = null;
    }

    private ProductModel(Long id, Long brandId, String name, String description, String imageUrl,
                         Long price, Integer stock, Long likesCount, ZonedDateTime deletedAt) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.stock = new Stock(stock);
        this.likesCount = likesCount;
        this.deletedAt = deletedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static ProductModel reconstitute(Long id, Long brandId, String name, String description, String imageUrl,
                                            Long price, Integer stock, Long likesCount, ZonedDateTime deletedAt) {
        return new ProductModel(id, brandId, name, description, imageUrl, price, stock, likesCount, deletedAt);
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

    /** 활성 여부 — deletedAt이 null이면 활성 (01 §7.5). */
    public boolean isActive() {
        return deletedAt == null;
    }

    /** soft delete. 멱등 — 이미 삭제됐으면 시각을 유지한다 (Brand→Product cascade — 01 §7.5). */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /** soft delete 복원. 멱등. */
    public void restore() {
        this.deletedAt = null;
    }

    public void update(String newName, String newDescription, String newImageUrl, Long newPrice, Integer newStock) {
        this.name = validateName(newName);
        this.description = validateDescription(newDescription);
        this.imageUrl = validateImageUrl(newImageUrl);
        this.price = validatePrice(newPrice);
        this.stock = new Stock(newStock);
    }

    // --- Getter ---

    public Long getId() {
        return id;
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

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
