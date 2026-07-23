package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ranking_daily_snapshot 조회 전용 JPA 리포지토리. 쓰기는 commerce-batch 가 JdbcTemplate 로 한다.
 */
public interface RankingSnapshotJpaRepository
        extends JpaRepository<RankingSnapshotEntity, RankingSnapshotEntity.Pk> {

    /** 해당 일자의 순위 오름차순(1위부터) 한 페이지. 순위는 적재 시점에 확정된 rank_no 를 그대로 쓴다. */
    List<RankingSnapshotEntity> findByRankingDateOrderByRankNoAsc(LocalDate rankingDate, Pageable pageable);

    long countByRankingDate(LocalDate rankingDate);

    Optional<RankingSnapshotEntity> findByRankingDateAndProductId(LocalDate rankingDate, Long productId);
}
