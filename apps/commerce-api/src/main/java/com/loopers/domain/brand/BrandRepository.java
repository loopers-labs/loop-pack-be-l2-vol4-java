package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandEntity save(BrandEntity brand);
    Optional<BrandEntity> findById(Long id);
    List<BrandEntity> findAllByIds(List<Long> ids);
    Page<BrandEntity> findAll(Pageable pageable);
    Optional<BrandEntity> findByName(String name);
}
