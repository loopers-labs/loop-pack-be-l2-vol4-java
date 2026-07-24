package com.loopers.infrastructure.ranking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface ProductBatchJpaRepository extends JpaRepository<ProductBatchJpaEntity, Long> {

    @Query("""
        select p.id
        from ProductBatchJpaEntity p
        where p.id in :productIds
          and p.deleted = false
        """)
    Set<Long> findActiveProductIds(@Param("productIds") Collection<Long> productIds);
}
