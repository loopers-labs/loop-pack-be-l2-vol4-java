package com.loopers.infrastructure.ranking;

import java.io.Serializable;
import java.util.Objects;

/**
 * mv_product_rank_monthly 의 복합 식별자 — (year_month, product_id).
 */
public class MvProductRankMonthlyId implements Serializable {

    private String yearMonth;
    private Long productId;

    protected MvProductRankMonthlyId() {}

    public MvProductRankMonthlyId(String yearMonth, Long productId) {
        this.yearMonth = yearMonth;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MvProductRankMonthlyId that)) {
            return false;
        }
        return Objects.equals(yearMonth, that.yearMonth) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(yearMonth, productId);
    }
}
