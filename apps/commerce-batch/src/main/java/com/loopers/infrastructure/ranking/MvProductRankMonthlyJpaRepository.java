package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, MvProductRankMonthlyId> {

    List<MvProductRankMonthly> findByYearMonthOrderByRankNoAsc(String yearMonth);

    /**
     * 재집계 멱등의 앞단(D7): 그 달 전체를 먼저 지운다. year_month 는 MySQL 예약어라 백틱으로 감싼다.
     */
    @Modifying
    @Query(value = "DELETE FROM mv_product_rank_monthly WHERE `year_month` = :yearMonth", nativeQuery = true)
    void deleteByYearMonth(@Param("yearMonth") String yearMonth);

    /**
     * 기간(start~end)의 daily 를 상품별로 롤업해 score 를 매기고, 내림차순 rank 를 붙여 TOP 100 을 적재한다.
     * score = Σview×0.1 + Σlike×0.2 + Σsales×0.7 (D2+). 주간과 동일 공식, 버킷만 달(月)이다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO mv_product_rank_monthly (`year_month`, product_id, score, rank_no, updated_at)
        SELECT :yearMonth, product_id, score, ROW_NUMBER() OVER (ORDER BY score DESC), NOW()
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
    void insertMonthlyTop100(
        @Param("yearMonth") String yearMonth,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
