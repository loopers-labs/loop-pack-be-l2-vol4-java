package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {

    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);

    Page<BrandModel> findByDeletedAtIsNull(Pageable pageable);

    Page<BrandModel> findByNameValueContainingAndDeletedAtIsNull(String keyword, Pageable pageable);
}