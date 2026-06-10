package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);
    List<BrandModel> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);
    Page<BrandModel> findAllByDeletedAtIsNull(Pageable pageable);
}
