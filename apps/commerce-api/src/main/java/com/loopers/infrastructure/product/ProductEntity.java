package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * product 테이블 JPA 매핑 전용 엔티티. 순수 도메인(ProductModel)과 분리되어 영속 관심사만 담는다.
 * Stock VO는 여기서 Integer stock 컬럼으로 풀어 매핑한다(컬럼명 동일 → 스키마 변경 없음).
 * soft delete는 BaseEntity의 deletedAt/delete()/restore()를 그대로 사용한다.
 * 도메인 ↔ 엔티티 변환은 ProductEntityMapper가 담당.
 */
@Entity
@Table(name = "product")
public class ProductEntity extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    protected ProductEntity() {}

    public ProductEntity(Long brandId, String name, String description, String imageUrl,
                         Long price, Integer stock, Long likesCount) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.stock = stock;
        this.likesCount = likesCount;
    }

    /**
     * 변경 가능한 상태(이름/설명/이미지/가격/재고/좋아요 수)만 갱신한다.
     * brandId는 불변. managed 엔티티에 적용 → dirty checking이 UPDATE로 반영.
     * (soft delete 동기화는 BaseEntity.delete()/restore()로 별도 처리)
     */
    public void applyState(String name, String description, String imageUrl, Long price, Integer stock, Long likesCount) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.stock = stock;
        this.likesCount = likesCount;
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
        return stock;
    }

    public Long getLikesCount() {
        return likesCount;
    }
}
