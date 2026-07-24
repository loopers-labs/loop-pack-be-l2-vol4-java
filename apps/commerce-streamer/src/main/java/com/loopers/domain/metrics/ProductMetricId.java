package com.loopers.domain.metrics;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * ProductMetricModel의 복합 PK(@IdClass). (집계일자, 상품ID)로 상품 × 날짜당 한 행을 식별한다.
 * 필드명은 엔티티의 @Id 필드명(statDate, productId)과 정확히 일치해야 한다.
 */
public class ProductMetricId implements Serializable {

    private LocalDate statDate;
    private Long productId;

    protected ProductMetricId() {}

    public ProductMetricId(LocalDate statDate, Long productId) {
        this.statDate = statDate;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMetricId that)) return false;
        return Objects.equals(statDate, that.statDate) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statDate, productId);
    }
}
