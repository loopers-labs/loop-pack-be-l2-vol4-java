package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, Long> {

    /** 벌크 삭제. 파생 delete(deleteByPeriodKey)는 대상 엔티티를 전부 로드한 뒤 건별로 지우므로 쓰지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MvProductRankMonthly r where r.periodKey = :periodKey")
    int deleteByPeriodKey(@Param("periodKey") String periodKey);
}
