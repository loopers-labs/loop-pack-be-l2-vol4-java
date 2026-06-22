package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> findById(Long id);
    boolean existsByName(String name);
    Page<BrandModel> findAll(PageRequest pageRequest);
}
