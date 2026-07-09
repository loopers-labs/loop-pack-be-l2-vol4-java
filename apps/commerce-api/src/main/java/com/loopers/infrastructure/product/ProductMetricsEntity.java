package com.loopers.infrastructure.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * product_metrics — 상품 리스팅 read model (CQRS read model). {@code product_id} 를 PK로 product 와 1:1.
 *
 * <p><b>2-writer 컬럼 소유 분리</b>가 이 테이블의 핵심 설계다. 리스팅 쿼리가 단일 테이블에서 정렬/필터를
 * 끝내려면 정렬·필터 키가 모두 이 테이블에 있어야 하므로, 컬럼 소유권을 두 앱으로 나눈다:
 * <ul>
 *   <li><b>차원(commerce-api 소유)</b>: {@code brand_id, price, deleted_at} — 상품 생성/수정/삭제
 *       라이프사이클과 함께 갱신한다(행 생성·삭제도 commerce-api 소유).</li>
 *   <li><b>측정값(commerce-streamer 소유)</b>: {@code like_count, sales_count, view_count} — 이벤트
 *       집계로 {@code +=} 만 한다(가산=교환법칙 → 순서 무관, event_handled 로 멱등).</li>
 * </ul>
 * 두 writer 는 <b>서로 다른 컬럼만</b> targeted UPDATE(commerce-api=@Modifying, streamer=JdbcTemplate)로
 * 갱신하므로 JPA dirty-checking 전체행 UPDATE로 인한 상호 clobber 가 없다. 그래서 이 엔티티에는
 * 상태 변경 메서드를 두지 않고(읽기 + 스키마 정의 전용), 쓰기는 모두 명시적 UPDATE 로 처리한다.
 *
 * <p>인덱스(좋아요순 DESC 복합)는 {@code product} 와 같은 이유로 JPA {@code @Index} 로 선언하지 않고
 * {@code resources/import.sql}(local/test) / migration SQL(prd)로 직접 만든다 —
 * Hibernate {@code @Index} 는 컬럼 방향(DESC)을 표현하지 못하기 때문이다.
 * <pre>
 *   idx_pm_active_likes_desc       (deleted_at, like_count DESC, product_id DESC)            -- 전체 + 좋아요순
 *   idx_pm_brand_active_likes_desc (brand_id, deleted_at, like_count DESC, product_id DESC)  -- 브랜드 필터 + 좋아요순
 * </pre>
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetricsEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    // --- 차원 (commerce-api 소유) ---
    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    // --- 측정값 (commerce-streamer 소유, 이벤트 집계로 += ) ---
    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    // --- 감사(audit) — 쓰기 경로(native UPDATE/JdbcTemplate)가 직접 now() 로 설정 ---
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricsEntity() {}

    public Long getProductId() {
        return productId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getPrice() {
        return price;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
