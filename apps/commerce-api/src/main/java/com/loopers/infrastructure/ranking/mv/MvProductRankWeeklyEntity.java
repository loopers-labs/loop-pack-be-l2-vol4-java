package com.loopers.infrastructure.ranking.mv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * mv_product_rank_weekly 조회 전용 매핑. commerce-batch가 채우고 이 앱은 읽기만 한다.
 * 같은 테이블이지만 apps 간 모듈 경계상 별도 엔티티로 둔다(§3.1).
 */
@Getter
@Entity(name = "MvProductRankWeeklyRead")
@Table(
    name = "mv_product_rank_weekly",
    indexes = @Index(name = "uk_mv_weekly_rank", columnList = "period_key, rank_position", unique = true)
)
@IdClass(MvProductRankWeeklyEntity.MvProductRankWeeklyId.class)
public class MvProductRankWeeklyEntity {

    @Id
    @Column(name = "period_key")
    private String periodKey;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected MvProductRankWeeklyEntity() {}

    public static class MvProductRankWeeklyId implements Serializable {
        private String periodKey;
        private Long productId;

        public MvProductRankWeeklyId() {}
    }
}
