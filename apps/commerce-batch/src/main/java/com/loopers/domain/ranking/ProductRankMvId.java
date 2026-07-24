package com.loopers.domain.ranking;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ProductRankMvId implements Serializable {

    private LocalDate periodStartDate;
    private Long productId;

    protected ProductRankMvId() {}

    public ProductRankMvId(LocalDate periodStartDate, Long productId) {
        this.periodStartDate = periodStartDate;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductRankMvId that)) return false;
        return Objects.equals(periodStartDate, that.periodStartDate)
            && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodStartDate, productId);
    }
}
