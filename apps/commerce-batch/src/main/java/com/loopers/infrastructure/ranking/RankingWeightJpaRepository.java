package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingWeightEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingWeightJpaRepository extends JpaRepository<RankingWeightEntity, String> {}
