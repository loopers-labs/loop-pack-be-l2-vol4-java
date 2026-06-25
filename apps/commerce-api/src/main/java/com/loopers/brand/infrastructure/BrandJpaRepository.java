package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByIdAndDeletedAtIsNull(Long brandId);
    List<Brand> findByIdInAndDeletedAtIsNull(Collection<Long> brandIds);
    Page<Brand> findByDeletedAtIsNull(Pageable pageable);
}
