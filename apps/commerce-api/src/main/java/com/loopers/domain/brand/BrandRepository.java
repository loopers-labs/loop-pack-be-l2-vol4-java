package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
    Page<BrandModel> findAll(Pageable pageable);
    Map<Long, BrandModel> findAllByIds(Collection<Long> ids);
    boolean existsByName(String name);
    void delete(Long id);
}
