package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvActiveVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MvActiveVersionJpaRepository extends JpaRepository<MvActiveVersionEntity, String> {}
