package com.loopers.infrastructure.ranking;

import com.loopers.batch.domain.ranking.MonthlyProductRankMv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * 월간 MV 접근. writer 의 saveAll 은 상속. deleteBySnapshotDate 는 해당 스냅샷을 통째로 지우는 window 교체용(재실행 멱등, week10 qna Q4).
 */
public interface MonthlyProductRankMvJpaRepository extends JpaRepository<MonthlyProductRankMv, Long> {

    @Modifying
    @Query("DELETE FROM MonthlyProductRankMv m WHERE m.snapshotDate = :snapshotDate")
    void deleteBySnapshotDate(@Param("snapshotDate") LocalDate snapshotDate);
}
