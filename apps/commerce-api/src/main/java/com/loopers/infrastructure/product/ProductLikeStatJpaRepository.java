package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductLikeStatJpaRepository extends JpaRepository<ProductLikeStat, Long> {
    List<ProductLikeStat> findAllByProductIdIn(List<Long> productIds);
}
