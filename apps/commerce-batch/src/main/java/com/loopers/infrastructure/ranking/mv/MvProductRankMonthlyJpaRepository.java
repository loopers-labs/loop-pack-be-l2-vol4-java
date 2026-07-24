package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository
    extends JpaRepository<MvProductRankMonthlyEntity, MvProductRankMonthlyEntity.MvProductRankMonthlyId> {

    @Modifying(clearAutomatically = true)
    @Query("delete from MvProductRankMonthly m where m.periodKey = :periodKey")
    void deleteByPeriodKey(@Param("periodKey") String periodKey);

    List<MvProductRankMonthlyEntity> findByPeriodKeyOrderByRankPositionAsc(String periodKey);
}
