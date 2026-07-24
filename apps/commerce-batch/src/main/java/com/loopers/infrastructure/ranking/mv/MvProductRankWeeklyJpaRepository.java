package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository
    extends JpaRepository<MvProductRankWeeklyEntity, MvProductRankWeeklyEntity.MvProductRankWeeklyId> {

    @Modifying(clearAutomatically = true)
    @Query("delete from MvProductRankWeekly w where w.periodKey = :periodKey")
    void deleteByPeriodKey(@Param("periodKey") String periodKey);

    List<MvProductRankWeeklyEntity> findByPeriodKeyOrderByRankPositionAsc(String periodKey);
}
