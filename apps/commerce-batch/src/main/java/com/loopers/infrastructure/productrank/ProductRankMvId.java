package com.loopers.infrastructure.productrank;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class ProductRankMvId implements Serializable {

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "product_id", length = 60, nullable = false)
    private String productId;

    protected ProductRankMvId() {}

    public ProductRankMvId(LocalDate asOfDate, String productId) {
        this.asOfDate = asOfDate;
        this.productId = productId;
    }

    public LocalDate getAsOfDate() { return asOfDate; }
    public String getProductId() { return productId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductRankMvId that)) return false;
        return Objects.equals(asOfDate, that.asOfDate) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asOfDate, productId);
    }
}
