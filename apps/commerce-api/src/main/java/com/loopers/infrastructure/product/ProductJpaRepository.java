package com.loopers.infrastructure.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
    Optional<ProductJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<ProductJpaEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductJpaEntity p where p.id in :ids and p.deletedAt is null order by p.id asc")
    List<ProductJpaEntity> findAllByIdInAndDeletedAtIsNullForUpdate(@Param("ids") List<Long> ids);

    List<ProductJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    List<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Modifying
    @Query("update ProductJpaEntity p set p.likeCount = :likeCount where p.id = :productId and p.deletedAt is null")
    int updateLikeCount(@Param("productId") Long productId, @Param("likeCount") Integer likeCount);
}
