package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * Product Aggregate 루트 — 순수 도메인 객체. 좋아요 수/활성 상태 같은 비즈니스 규칙만 보유하고
 * 영속 기술(JPA)에는 의존하지 않는다. JPA 매핑은 infrastructure.product.ProductEntity가 담당하고,
 * 도메인 ↔ 엔티티 변환은 ProductEntityMapper가 처리한다.
 *
 * 재고는 독립 Aggregate(domain.stock.StockModel)로 분리되어 productId(ID 참조)로만 연결한다.
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
    private ZonedDateTime deletedAt;   // null이면 활성 (soft delete, 01 §7.5)

    // 좋아요 수는 더 이상 product 의 상태가 아니다. 비동기 집계 read model(product_metrics.like_count)이
    // 진실원천이며(week7 CQRS), 조회 조립 시 ProductMetricsService 로 별도 주입한다.

    public ProductModel(Long brandId, String name, String description, String imageUrl, Long price) {
        this.id = null;
        this.brandId = validateBrandId(brandId);
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.imageUrl = validateImageUrl(imageUrl);
        this.price = validatePrice(price);
        this.deletedAt = null;
    }

    private ProductModel(Long id, Long brandId, String name, String description, String imageUrl,
                         Long price, ZonedDateTime deletedAt) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.deletedAt = deletedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static ProductModel reconstitute(Long id, Long brandId, String name, String description, String imageUrl,
                                            Long price, ZonedDateTime deletedAt) {
        return new ProductModel(id, brandId, name, description, imageUrl, price, deletedAt);
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

    public void update(String newName, String newDescription, String newImageUrl, Long newPrice) {
        this.name = validateName(newName);
        this.description = validateDescription(newDescription);
        this.imageUrl = validateImageUrl(newImageUrl);
        this.price = validatePrice(newPrice);
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

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
