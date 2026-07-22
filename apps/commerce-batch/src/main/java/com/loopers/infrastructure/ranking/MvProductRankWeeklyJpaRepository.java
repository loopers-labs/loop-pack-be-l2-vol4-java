package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, MvProductRankWeeklyId> {

    List<MvProductRankWeekly> findByYearWeekOrderByRankNoAsc(String yearWeek);

    /**
     * 재집계 멱등의 앞단(D7): 그 주 전체를 먼저 지운다. 이어지는 INSERT 와 한 트랜잭션으로 묶여
     * TOP 100 밖으로 밀려난 '유령 행'을 남기지 않는다.
     */
    @Modifying
    @Query(value = "DELETE FROM mv_product_rank_weekly WHERE year_week = :yearWeek", nativeQuery = true)
    void deleteByYearWeek(@Param("yearWeek") String yearWeek);

    /**
     * 기간(start~end)의 daily 를 상품별로 롤업해 score 를 매기고, 내림차순 rank 를 붙여 TOP 100 을 적재한다.
     * score = Σview×0.1 + Σlike×0.2 + Σsales×0.7 (D2+). rank 는 ROW_NUMBER 로 부여(MySQL 8 window).
     */
    @Modifying
    @Query(value = """
        INSERT INTO mv_product_rank_weekly (year_week, product_id, score, rank_no, updated_at)
        SELECT :yearWeek, product_id, score, ROW_NUMBER() OVER (ORDER BY score DESC), NOW()
        FROM (
            SELECT product_id,
                   SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + SUM(sales_count) * 0.7 AS score
            FROM daily_product_metrics
            WHERE metric_date BETWEEN :startDate AND :endDate
            GROUP BY product_id
        ) agg
        ORDER BY score DESC
        LIMIT 100
        """, nativeQuery = true)
    void insertWeeklyTop100(
        @Param("yearWeek") String yearWeek,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
