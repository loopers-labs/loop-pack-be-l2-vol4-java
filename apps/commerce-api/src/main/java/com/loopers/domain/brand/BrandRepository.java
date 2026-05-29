package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> findById(Long id);
    boolean existsByName(String name);
    Page<BrandModel> findAll(Pageable pageable);
    List<BrandModel> findAllByIds(List<Long> ids);
}
