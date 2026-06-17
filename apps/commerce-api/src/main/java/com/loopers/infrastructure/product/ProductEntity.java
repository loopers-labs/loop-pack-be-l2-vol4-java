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
 * <p>인덱스 (week5 — 상품 목록 조회 최적화)는 <b>JPA {@code @Index} 로 선언하지 않는다.</b>
 * 목록 정렬이 {@code ORDER BY likes_count DESC, id DESC} 라, 정렬 컬럼에 <b>방향(DESC)을 지정한
 * 내림차순 인덱스</b>(forward index scan)가 backward scan 보다 딥 페이지에서 유리하고, 향후
 * mixed-direction 정렬(예: {@code likes_count DESC, id ASC})은 방향 지정 인덱스가 아니면 filesort 를
 * 피할 수 없기 때문이다. 그런데 Hibernate {@code @Index} 는 컬럼 방향을 표현하지 못한다.
 * 따라서 인덱스는 DDL 로 직접 정의한다:
 * <ul>
 *   <li>local/test ({@code ddl-auto: create}) — {@code resources/import.sql} 가 스키마 생성 직후 실행</li>
 *   <li>prd ({@code ddl-auto: none}) — {@code docs/week5/migration_product_indexes.sql} 를 운영 DDL 로 적용</li>
 * </ul>
 * <pre>
 *   idx_brand_active_likes_desc (brand_id, deleted_at, likes_count DESC, id DESC)  -- 브랜드 필터 + 좋아요순
 *   idx_active_likes_desc       (deleted_at, likes_count DESC, id DESC)            -- 전체 + 좋아요순
 * </pre>
 * 설계·측정 근거: docs/week5/05-index-optimization.md, benchmark/
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

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    protected ProductEntity() {}

    public ProductEntity(Long brandId, String name, String description, String imageUrl,
                         Long price, Long likesCount) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.likesCount = likesCount;
    }

    /**
     * 변경 가능한 상태(이름/설명/이미지/가격/좋아요 수)만 갱신한다.
     * brandId는 불변. managed 엔티티에 적용 → dirty checking이 UPDATE로 반영.
     * (soft delete 동기화는 BaseEntity.delete()/restore()로 별도 처리)
     */
    public void applyState(String name, String description, String imageUrl, Long price, Long likesCount) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
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

    public Long getLikesCount() {
        return likesCount;
    }
}
