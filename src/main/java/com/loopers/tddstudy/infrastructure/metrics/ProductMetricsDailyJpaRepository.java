package com.loopers.tddstudy.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ProductMetricsDailyJpaRepository
        extends JpaRepository<ProductMetricsDaily, Long> {

    void deleteByMetricDate(LocalDate metricDate);

    @Query("""
           select new com.loopers.tddstudy.infrastructure.metrics.MetricSum(
               coalesce(sum(d.likeCount), 0),
               coalesce(sum(d.salesCount), 0),
               coalesce(sum(d.viewCount), 0))
           from ProductMetricsDaily d
           where d.productId = :productId
           """)
    MetricSum sumByProductId(@Param("productId") Long productId);


    @Query("""
           select new com.loopers.tddstudy.infrastructure.metrics.ProductScoreSum(
               d.productId,
               sum(d.likeCount),
               sum(d.salesCount),
               sum(d.viewCount),
               cast(sum(d.likeCount) as Double) * :likeWeight
                 + cast(sum(d.salesCount) as Double) * :salesWeight)
           from ProductMetricsDaily d
           where d.metricDate between :start and :end
           group by d.productId
           order by cast(sum(d.likeCount) as Double) * :likeWeight
                 + cast(sum(d.salesCount) as Double) * :salesWeight desc
           """)
    List<ProductScoreSum> aggregateTopScores(@Param("start") LocalDate start,
                                             @Param("end") LocalDate end,
                                             @Param("likeWeight") double likeWeight,
                                             @Param("salesWeight") double salesWeight,
                                             Pageable pageable);

}
