package com.loopers.infrastructure.brand;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.brand.BrandModel;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {

    boolean existsByNameValueAndDeletedAtIsNull(String value);

    boolean existsByNameValueAndDeletedAtIsNullAndIdNot(String value, Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);

    Page<BrandModel> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
}
