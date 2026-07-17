package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RankingSnapshotJpaRepository extends JpaRepository<RankingSnapshot, Long> {

    void deleteByStatDate(LocalDate statDate);

    List<RankingSnapshot> findByStatDateOrderByRankPositionAsc(LocalDate statDate);
}
