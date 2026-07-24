package com.loopers.ranking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "product_rank_candidates",
    indexes = @Index(
        name = "idx_product_rank_candidates_snapshot_score_product",
        columnList = "snapshot_id, score DESC, product_id ASC"
    )
)
public class ProductRankCandidate {

    @EmbeddedId
    private ProductRankId id;

    @MapsId("snapshotId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "snapshot_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_product_rank_candidates_snapshot_id",
            foreignKeyDefinition = """
                foreign key (snapshot_id)
                references product_rank_snapshots(id)
                on delete restrict
                """
        )
    )
    private ProductRankSnapshot snapshot;

    @Column(name = "score", nullable = false)
    private double score;
}
