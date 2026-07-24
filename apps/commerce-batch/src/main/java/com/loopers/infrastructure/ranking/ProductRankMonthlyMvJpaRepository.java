package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankMonthlyMvJpaRepository extends JpaRepository<ProductRankMonthlyMvJpaEntity, Long> {

    List<ProductRankMonthlyMvJpaEntity> findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Pageable pageable
    );

    @Query("""
        select count(e) <> count(distinct e.rankNo)
        from ProductRankMonthlyMvJpaEntity e
        where e.batchRunId = :batchRunId
        """)
    boolean existsDuplicateRankNo(@Param("batchRunId") Long batchRunId);

    @Query("""
        select count(e) <> count(distinct e.productId)
        from ProductRankMonthlyMvJpaEntity e
        where e.batchRunId = :batchRunId
        """)
    boolean existsDuplicateProductId(@Param("batchRunId") Long batchRunId);

    @Modifying
    @Query("""
        update ProductRankMonthlyMvJpaEntity e
        set e.active = false
        where e.rankStartDate = :rankStartDate
          and e.rankEndDate = :rankEndDate
          and e.active = true
        """)
    void deactivateActiveSnapshot(
        @Param("rankStartDate") LocalDate rankStartDate,
        @Param("rankEndDate") LocalDate rankEndDate
    );

    @Modifying
    @Query("""
        update ProductRankMonthlyMvJpaEntity e
        set e.active = true
        where e.batchRunId = :batchRunId
        """)
    void activateSnapshot(@Param("batchRunId") Long batchRunId);
}
