package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);

    List<Brand> findAllByDeletedAtIsNull();

    Page<Brand> findAllByDeletedAtIsNull(Pageable pageable);

    List<Brand> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);
}
