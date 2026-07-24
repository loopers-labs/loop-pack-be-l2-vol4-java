package com.loopers.domain.ranking;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * MV 랭킹 테이블의 복합 PK(@IdClass). (기간 시작일, 상품ID)로 "한 기간에 한 상품 1행"을 식별한다.
 * 필드명은 엔티티의 @Id 필드명(periodStart, productId)과 정확히 일치해야 한다.
 */
public class ProductRankId implements Serializable {

    private LocalDate periodStart;
    private Long productId;

    protected ProductRankId() {}

    public ProductRankId(LocalDate periodStart, Long productId) {
        this.periodStart = periodStart;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductRankId that)) return false;
        return Objects.equals(periodStart, that.periodStart) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodStart, productId);
    }
}
