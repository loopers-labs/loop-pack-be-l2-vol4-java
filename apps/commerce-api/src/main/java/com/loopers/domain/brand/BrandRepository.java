package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
    Optional<BrandModel> findActive(Long id);
    List<BrandModel> findAllByIdIn(Collection<Long> ids);
    Page<BrandModel> findAll(Pageable pageable);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
