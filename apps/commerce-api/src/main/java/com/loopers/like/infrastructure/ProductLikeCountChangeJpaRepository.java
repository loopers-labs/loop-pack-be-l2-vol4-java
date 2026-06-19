package com.loopers.like.infrastructure;

import com.loopers.like.domain.ProductLikeCountChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductLikeCountChangeJpaRepository extends JpaRepository<ProductLikeCountChange, Long> {
}
