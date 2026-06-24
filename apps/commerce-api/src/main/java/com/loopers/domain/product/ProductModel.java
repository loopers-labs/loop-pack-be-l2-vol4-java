package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * <p><strong>인덱스 설계 (Round 5)</strong>:
 * <ul>
 *   <li>브랜드 필터 + 정렬: {@code (brand_id, created_at)}, {@code (brand_id, price)}, {@code (brand_id, like_count)}
 *       — 선두 brand_id 로 거르고 다음 컬럼이 정렬을 커버해 filesort 를 제거.</li>
 *   <li>전체(브랜드 무관) 정렬: {@code (created_at)}, {@code (price)}, {@code (like_count)}
 *       — brand_id 선두 복합 인덱스는 brandId 가 없으면 못 쓰므로, 전체 정렬용 단일 인덱스를 별도로 둔다.</li>
 * </ul>
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_brand_created", columnList = "brand_id, created_at"),
    @Index(name = "idx_products_brand_price", columnList = "brand_id, price"),
    @Index(name = "idx_products_brand_likecount", columnList = "brand_id, like_count"),
    @Index(name = "idx_products_created", columnList = "created_at"),
    @Index(name = "idx_products_price", columnList = "price"),
    @Index(name = "idx_products_likecount", columnList = "like_count")
})
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    /**
     * 좋아요 수 (비정규화). 정렬·표시 성능을 위해 likes 테이블 집계 대신 이 컬럼을 사용한다.
     * 좋아요 등록/취소 시 조건부 원자 UPDATE 로만 갱신되며, 엔티티 setter 는 두지 않는다.
     */
    @Column(name = "like_count", nullable = false)
    private long likeCount = 0L;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, Long price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 필수입니다.");
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
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = Money.of(price);   // Money 생성 시 음수 방지 검증
        this.status = ProductStatus.ON_SALE;
    }

    public void update(String newName, String newDescription, Long newPrice) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        this.name = newName;
        this.description = newDescription;
        this.price = Money.of(newPrice);
    }

    /** BaseEntity.delete() 오버라이드 - deleted_at 채움 + status = DELETED 동기화. */
    @Override
    public void delete() {
        super.delete();
        this.status = ProductStatus.DELETED;
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

    /** 가격 (DTO/응답용 — Long). 도메인 내부에서는 {@link Money} 로 캡슐화되어 있다. */
    public Long getPrice() {
        return price.getAmount();
    }

    public ProductStatus getStatus() {
        return status;
    }

    /** 좋아요 수 (비정규화 컬럼). */
    public long getLikeCount() {
        return likeCount;
    }
}
