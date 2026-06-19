package com.loopers.infrastructure.productrank;

import com.loopers.domain.productrank.ProductRank;
import com.loopers.domain.productrank.ProductRankRepository;
import com.loopers.domain.productrank.RankedProduct;
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
    public List<RankedProduct> findRankedByBrandLikesDesc(Long brandId, Long lastLikeCount, Long lastProductId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT product_id, like_count FROM product_rank WHERE 1 = 1");
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
        List<Object[]> rows = query.getResultList();
        return rows.stream()
            .map(row -> new RankedProduct(((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
            .toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<ProductRank> ranks) {
        jpa.deleteAllInBatch();
        jpa.saveAll(ranks);
    }

    @Override
    @Transactional
    public void rebuildFromSource() {
        em.createNativeQuery("DELETE FROM product_rank").executeUpdate();
        em.createNativeQuery("""
            INSERT INTO product_rank (product_id, brand_id, like_count, created_at, updated_at)
            SELECT p.id, p.brand_id, COALESCE(plc.like_count, 0), NOW(), NOW()
            FROM product p
            LEFT JOIN product_like_count plc ON plc.product_id = p.id
            WHERE p.deleted_at IS NULL
            """).executeUpdate();
    }
}
