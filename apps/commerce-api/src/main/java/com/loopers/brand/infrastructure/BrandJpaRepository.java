package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.BrandModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT b FROM BrandModel b WHERE b.id IN :ids AND b.deletedAt IS NULL")
    List<BrandModel> findAllByIds(@Param("ids") List<Long> ids);
}
