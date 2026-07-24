package com.loopers.ranking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mv_product_rank_monthly_snapshot_id_rank_no",
        columnNames = {"snapshot_id", "rank_no"}
    )
)
public class MonthlyProductRank {

    @EmbeddedId
    private ProductRankId id;

    @MapsId("snapshotId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "snapshot_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_mv_product_rank_monthly_snapshot_id",
            foreignKeyDefinition = """
                foreign key (snapshot_id)
                references product_rank_snapshots(id)
                on delete restrict
                """
        )
    )
    private ProductRankSnapshot snapshot;

    @Check(
        name = "ck_mv_product_rank_monthly_rank_no_range",
        constraints = "rank_no between 1 and 100"
    )
    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "score", nullable = false)
    private double score;
}
