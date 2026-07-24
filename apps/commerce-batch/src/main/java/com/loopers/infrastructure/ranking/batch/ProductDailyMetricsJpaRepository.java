package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingDailyMetricsAggregate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProductDailyMetricsJpaRepository
    extends JpaRepository<ProductDailyMetricsEntity, ProductDailyMetricsEntity.DailyMetricsId> {

    @Query(
        value = """
            select new com.loopers.domain.ranking.batch.RankingDailyMetricsAggregate(
                p.productId, sum(p.orderCount), sum(p.likeCount), sum(p.viewCount))
            from ProductDailyMetricsForRanking p
            where p.metricDate between :startDate and :endDate
            group by p.productId
            """,
        countQuery = """
            select count(distinct p.productId)
            from ProductDailyMetricsForRanking p
            where p.metricDate between :startDate and :endDate
            """
    )
    Page<RankingDailyMetricsAggregate> aggregateByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );
}
