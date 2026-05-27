package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Optional<BrandModel> findById(Long id);
    List<BrandModel> findAllByIdIn(Collection<Long> ids);
    Page<BrandModel> findAll(Pageable pageable);
    BrandModel save(BrandModel brand);
}
