package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;

// 주간 TOP 100 랭킹 MV 의 조회 모델. 적재는 commerce-batch 가 JDBC 로 수행하고, API 는 이 엔티티로 읽는다.
// (로컬/테스트에서는 이 엔티티의 ddl-auto 로 테이블이 생성된다. week_start_date 는 해당 주의 월요일)
@Getter
@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mv_product_rank_weekly_date_product",
        columnNames = {"week_start_date", "product_id"}
    ),
    indexes = @Index(
        name = "idx_mv_product_rank_weekly_date_ranking",
        columnList = "week_start_date, ranking"
    )
)
public class MvProductRankWeeklyModel extends BaseEntity {

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private Long score;

    // MySQL 예약어 rank 를 피해 컬럼명은 ranking 을 사용한다. 1부터 시작하는 순위.
    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    protected MvProductRankWeeklyModel() {
    }

    public MvProductRankWeeklyModel(LocalDate weekStartDate, Long productId, Long score, Integer ranking) {
        this.weekStartDate = weekStartDate;
        this.productId = productId;
        this.score = score;
        this.ranking = ranking;
    }
}
