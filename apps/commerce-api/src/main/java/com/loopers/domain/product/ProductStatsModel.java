package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(
    name = "product_stats",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_stats_product_id", columnNames = "product_id"),
    indexes = @Index(name = "idx_product_stats_deleted_at_like_count", columnList = "deleted_at, like_count")
)
@SQLRestriction("deleted_at IS NULL")
public class ProductStatsModel extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductModel product;

    private Long likeCount;

    protected ProductStatsModel() {
    }

    public ProductStatsModel(ProductModel product) {
        this.product = product;
        this.likeCount = 0L;
    }
}
