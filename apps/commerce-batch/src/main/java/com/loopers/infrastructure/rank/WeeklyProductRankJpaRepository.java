package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.WeeklyProductRankModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface WeeklyProductRankJpaRepository extends JpaRepository<WeeklyProductRankModel, Long> {

    /**
     * 특정 기간 스냅샷만 벌크 삭제한다 — 다른 기간(과거 히스토리)은 보존.
     * 같은 baseDate 재실행 시 그 기간만 교체하는 멱등의 근거.
     */
    @Modifying
    @Query("DELETE FROM WeeklyProductRankModel w WHERE w.periodStart = :start AND w.periodEnd = :end")
    int deleteByPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
