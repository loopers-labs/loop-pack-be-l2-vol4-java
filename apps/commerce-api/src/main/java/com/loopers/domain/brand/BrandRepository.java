package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> findById(Long id);
    List<BrandModel> findAllByIds(Collection<Long> ids);
    Page<BrandModel> search(Pageable pageable);
}
