package com.loopers.infrastructure.brand;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {
    Optional<BrandEntity> findByIdAndDeletedAtIsNull(Long id);
    Page<BrandEntity> findAllByDeletedAtIsNull(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Brand b WHERE b.id = :id AND b.deletedAt IS NULL")
    Optional<BrandEntity> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);
}
