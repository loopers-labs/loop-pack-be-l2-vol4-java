package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, MvProductRankWeeklyId> {

    List<MvProductRankWeekly> findByYearWeekOrderByRankNoAsc(String yearWeek, Pageable pageable);
}
