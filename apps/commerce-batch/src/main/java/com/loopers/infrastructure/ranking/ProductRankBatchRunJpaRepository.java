package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.BatchRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRankBatchRunJpaRepository extends JpaRepository<ProductRankBatchRunJpaEntity, Long> {

    @Modifying
    @Query("update ProductRankBatchRunJpaEntity r set r.status = :status where r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") BatchRunStatus status);
}
