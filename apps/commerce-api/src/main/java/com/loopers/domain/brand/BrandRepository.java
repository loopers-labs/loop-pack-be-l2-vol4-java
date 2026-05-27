package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandRepository {

    BrandModel save(BrandModel brand);

    Optional<BrandModel> find(Long id);

    Page<BrandModel> findAllNotDeleted(Pageable pageable);

    Page<BrandModel> findByNameContainingAndNotDeleted(String keyword, Pageable pageable);
}