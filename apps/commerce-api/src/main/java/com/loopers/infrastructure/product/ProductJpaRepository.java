package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long productId);

    Page<Product> findByDeletedAtIsNull(Pageable pageable);

    Page<Product> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Query(
        value = """
            select p
            from Product p
            where p.deletedAt is null
              and (:brandId is null or p.brandId = :brandId)
              and exists (
                  select b.id
                  from Brand b
                  where b.id = p.brandId
                    and b.deletedAt is null
              )
            """,
        countQuery = """
            select count(p)
            from Product p
            where p.deletedAt is null
              and (:brandId is null or p.brandId = :brandId)
              and exists (
                  select b.id
                  from Brand b
                  where b.id = p.brandId
                    and b.deletedAt is null
              )
            """
    )
    Page<Product> findVisibleAll(@Param("brandId") Long brandId, Pageable pageable);

    @Query(
        value = """
            select p
            from Product p
            where p.deletedAt is null
              and (:brandId is null or p.brandId = :brandId)
              and exists (
                  select b.id
                  from Brand b
                  where b.id = p.brandId
                    and b.deletedAt is null
              )
            order by (
                select count(l.id)
                from Like l
                where l.productId = p.id
            ) desc, p.createdAt desc, p.id desc
            """,
        countQuery = """
            select count(p)
            from Product p
            where p.deletedAt is null
              and (:brandId is null or p.brandId = :brandId)
              and exists (
                  select b.id
                  from Brand b
                  where b.id = p.brandId
                    and b.deletedAt is null
              )
            """
    )
    Page<Product> findVisibleAllOrderByLikes(@Param("brandId") Long brandId, Pageable pageable);
}
