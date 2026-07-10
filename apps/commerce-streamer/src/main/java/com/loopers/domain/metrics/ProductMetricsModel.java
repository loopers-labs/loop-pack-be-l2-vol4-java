package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

// 좋아요·판매량·조회수 세 지표 모두 델타(누적) 모델이다 - +1/-1은 순서가 바뀌어도 합이 같아
// 원자적 UPSERT(INSERT ... ON DUPLICATE KEY UPDATE)만으로 동시성이 해결되고 버전락이 불필요하다.
// 값 변경은 ProductMetricsJpaRepository의 native UPSERT로만 이뤄진다 - 엔티티 자체엔 증감 메서드를 두지 않는다.
@Getter
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_metrics_product_id", columnNames = "product_id")
)
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    protected ProductMetricsModel() {
    }

    public ProductMetricsModel(Long productId) {
        this.productId = productId;
        this.likeCount = 0L;
        this.salesCount = 0L;
        this.viewCount = 0L;
    }
}
