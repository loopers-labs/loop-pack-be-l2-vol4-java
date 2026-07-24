package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;

// 월간 TOP 100 랭킹 MV 의 조회 모델. 적재는 commerce-batch 가 JDBC 로 수행하고, API 는 이 엔티티로 읽는다.
// (로컬/테스트에서는 이 엔티티의 ddl-auto 로 테이블이 생성된다. month_start_date 는 해당 월의 1일)
@Getter
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mv_product_rank_monthly_date_product",
        columnNames = {"month_start_date", "product_id"}
    ),
    indexes = @Index(
        name = "idx_mv_product_rank_monthly_date_ranking",
        columnList = "month_start_date, ranking"
    )
)
public class MvProductRankMonthlyModel extends BaseEntity {

    @Column(name = "month_start_date", nullable = false)
    private LocalDate monthStartDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private Long score;

    // MySQL 예약어 rank 를 피해 컬럼명은 ranking 을 사용한다. 1부터 시작하는 순위.
    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    protected MvProductRankMonthlyModel() {
    }

    public MvProductRankMonthlyModel(LocalDate monthStartDate, Long productId, Long score, Integer ranking) {
        this.monthStartDate = monthStartDate;
        this.productId = productId;
        this.score = score;
        this.ranking = ranking;
    }
}
