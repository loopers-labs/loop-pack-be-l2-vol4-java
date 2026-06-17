package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> findById(Long id);
    Page<BrandModel> findAll(Pageable pageable);
    void delete(Long id);
    boolean existsById(Long id);
}
