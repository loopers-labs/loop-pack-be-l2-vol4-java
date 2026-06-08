package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {

    @Query("SELECT b FROM BrandModel b WHERE b.id = :id AND b.deletedAt IS NULL")
    Optional<BrandModel> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT b FROM BrandModel b WHERE b.deletedAt IS NULL")
    List<BrandModel> findAllByDeletedAtIsNull(Pageable pageable);
}
