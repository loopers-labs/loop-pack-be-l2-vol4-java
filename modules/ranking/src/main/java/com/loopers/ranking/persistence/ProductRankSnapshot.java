package com.loopers.ranking.persistence;

import com.loopers.ranking.RankingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "product_rank_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_rank_snapshots_period_aggregation_end_date_revision",
        columnNames = {"period", "aggregation_end_date", "revision"}
    )
)
@Check(
    name = "ck_product_rank_snapshots_date_range",
    constraints = "period_start <= aggregation_end_date"
)
public class ProductRankSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Check(
        name = "ck_product_rank_snapshots_period",
        constraints = "period in ('WEEKLY', 'MONTHLY')"
    )
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 10)
    private RankingPeriod period;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "aggregation_end_date", nullable = false)
    private LocalDate aggregationEndDate;

    @Check(
        name = "ck_product_rank_snapshots_revision_positive",
        constraints = "revision >= 1"
    )
    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "score_policy_version", nullable = false, length = 30)
    private String scorePolicyVersion;

    @Column(name = "view_weight", nullable = false)
    private double viewWeight;

    @Column(name = "like_weight", nullable = false)
    private double likeWeight;

    @Column(name = "order_weight", nullable = false)
    private double orderWeight;

    @Check(
        name = "ck_product_rank_snapshots_order_amount_unit_positive",
        constraints = "order_amount_unit > 0"
    )
    @Column(name = "order_amount_unit", nullable = false)
    private long orderAmountUnit;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "completed_at", columnDefinition = "datetime(6)")
    private Instant completedAt;
}
