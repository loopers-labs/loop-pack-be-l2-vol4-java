package com.loopers.infrastructure.ranking;

import java.io.Serializable;
import java.util.Objects;

/**
 * mv_product_rank_weekly 의 복합 식별자 — (year_week, product_id).
 */
public class MvProductRankWeeklyId implements Serializable {

    private String yearWeek;
    private Long productId;

    protected MvProductRankWeeklyId() {}

    public MvProductRankWeeklyId(String yearWeek, Long productId) {
        this.yearWeek = yearWeek;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MvProductRankWeeklyId that)) {
            return false;
        }
        return Objects.equals(yearWeek, that.yearWeek) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(yearWeek, productId);
    }
}
