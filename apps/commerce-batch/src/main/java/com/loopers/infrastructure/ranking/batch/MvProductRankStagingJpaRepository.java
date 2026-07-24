package com.loopers.infrastructure.ranking.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MvProductRankStagingJpaRepository
    extends JpaRepository<MvProductRankStagingEntity, MvProductRankStagingEntity.StagingId> {

    @Modifying(clearAutomatically = true)
    @Query("delete from MvProductRankStaging s where s.periodType = :periodType and s.periodKey = :periodKey")
    void deleteByPeriodTypeAndPeriodKey(@Param("periodType") String periodType, @Param("periodKey") String periodKey);

    List<MvProductRankStagingEntity> findByPeriodTypeAndPeriodKeyOrderByRankPositionAsc(
        String periodType, String periodKey
    );
}
