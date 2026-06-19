package com.loopers.infrastructure.productrank;

import com.loopers.domain.productrank.ProductRank;
import com.loopers.domain.productrank.ProductRankRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductRankRepositoryImpl implements ProductRankRepository {

    private final ProductRankJpaRepository jpa;
    private final EntityManager em;

    @Override
    public List<Long> findIdsByBrandLikesDesc(Long brandId, Long lastLikeCount, Long lastProductId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT product_id FROM product_rank WHERE 1 = 1");
        if (brandId != null) {
            sql.append(" AND brand_id = :brandId");
        }
        if (lastLikeCount != null && lastProductId != null) {
            sql.append(" AND (like_count < :lc OR (like_count = :lc AND product_id < :lid))");
        }
        sql.append(" ORDER BY like_count DESC, product_id DESC");

        Query query = em.createNativeQuery(sql.toString());
        if (brandId != null) {
            query.setParameter("brandId", brandId);
        }
        if (lastLikeCount != null && lastProductId != null) {
            query.setParameter("lc", lastLikeCount);
            query.setParameter("lid", lastProductId);
        }
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Number> rows = query.getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<ProductRank> ranks) {
        jpa.deleteAllInBatch();
        jpa.saveAll(ranks);
    }
}
