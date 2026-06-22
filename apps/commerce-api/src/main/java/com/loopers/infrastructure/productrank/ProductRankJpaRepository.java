package com.loopers.infrastructure.productrank;

import com.loopers.domain.productrank.ProductRank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRankJpaRepository extends JpaRepository<ProductRank, Long> {
}
