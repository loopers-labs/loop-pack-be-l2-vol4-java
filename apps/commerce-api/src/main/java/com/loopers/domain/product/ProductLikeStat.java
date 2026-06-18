package com.loopers.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품의 좋아요 수 read-model (Materialized View 스타일).
 *
 * 정렬·필터 성능을 위해 Product 와는 별도 테이블로 분리한다.
 *  - PK = productId (1:1 관계)
 *  - brandId 는 비정규화: "brandId 필터 + 좋아요 정렬" 한 방에 풀기 위해 인덱스 첫 컬럼으로 사용
 *  - likeCount 는 좋아요 등록/취소 이벤트(AFTER_COMMIT)로 갱신됨
 *  - @Version: 동시 increment/decrement 시 손실 방지
 *
 * BaseEntity 상속 안 함: 자체 PK(productId)를 쓰고, createdAt 은 의미가 없으므로.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "product_like_stat",
    indexes = {
        @Index(name = "idx_pls_brand_likes", columnList = "brand_id, like_count"),
        @Index(name = "idx_pls_likes", columnList = "like_count")
    }
)
public class ProductLikeStat {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ProductLikeStat(Long productId, Long brandId, Long likeCount) {
        this.productId = productId;
        this.brandId = brandId;
        this.likeCount = likeCount;
        this.updatedAt = LocalDateTime.now();
    }

    /** 새 상품의 stat 을 0 으로 초기화한다. */
    public static ProductLikeStat init(Long productId, Long brandId) {
        return new ProductLikeStat(productId, brandId, 0L);
    }

    /** 백필이나 재계산 시 특정 값으로 생성한다. */
    public static ProductLikeStat of(Long productId, Long brandId, Long likeCount) {
        return new ProductLikeStat(productId, brandId, likeCount);
    }

    public void increment() {
        this.likeCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decrement() {
        if (this.likeCount > 0) {
            this.likeCount--;
            this.updatedAt = LocalDateTime.now();
        }
    }
}
