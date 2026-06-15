package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Page<ProductModel> findByBrandId(Long brandId, Pageable pageable);

    /** 재고 차감용 조회 — 비관적 쓰기 락 (SELECT ... FOR UPDATE) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductModel p where p.id = :id")
    Optional<ProductModel> findByIdForUpdate(@Param("id") Long id);

    /** 좋아요 수 증가 — 원자적 UPDATE (read-modify-write 회피) */
    @Modifying
    @Query("update ProductModel p set p.likeCount = p.likeCount + 1 where p.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    /** 좋아요 수 감소 — 0 미만으로 내려가지 않게 가드 */
    @Modifying
    @Query("update ProductModel p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);
}
