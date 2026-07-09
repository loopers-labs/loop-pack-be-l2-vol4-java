package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * product 테이블 JPA 매핑 전용 엔티티. 순수 도메인(ProductModel)과 분리되어 영속 관심사만 담는다.
 * 재고는 독립 Aggregate(stock 테이블)로 분리되어 이 엔티티에는 없다.
 * soft delete는 BaseEntity의 deletedAt/delete()/restore()를 그대로 사용한다.
 * 도메인 ↔ 엔티티 변환은 ProductEntityMapper가 담당.
 *
 * <p>좋아요 수(likes_count)는 week7 CQRS 전환으로 이 테이블에서 제거됐다 — 리스팅 정렬/표시는 비동기
 * 집계 read model(product_metrics)이 소유한다. 목록 정렬(좋아요순 포함)은 product_metrics 단일 테이블에서
 * 처리되므로 product 의 week5 좋아요순 복합 인덱스도 함께 제거됐다(product_metrics 로 이전).
 * product 는 이제 display 필드(name/description/image)를 id batch 로 공급하는 역할이다.
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

    protected ProductEntity() {}

    public ProductEntity(Long brandId, String name, String description, String imageUrl, Long price) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
    }

    /**
     * 변경 가능한 상태(이름/설명/이미지/가격)만 갱신한다. brandId는 불변.
     * managed 엔티티에 적용 → dirty checking이 UPDATE로 반영.
     * (soft delete 동기화는 BaseEntity.delete()/restore()로 별도 처리)
     * 좋아요 수는 product 상태가 아니라 product_metrics(read model)가 소유한다(week7 CQRS).
     */
    public void applyState(String name, String description, String imageUrl, Long price) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
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
}
