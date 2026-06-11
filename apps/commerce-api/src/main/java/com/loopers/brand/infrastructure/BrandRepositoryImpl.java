package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Brand> findAll() {
        return brandJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public List<Brand> findAllByIdIn(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public boolean existsById(Long id) {
        return brandJpaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByName(name);
    }

    @Override
    public int softDeleteById(Long id) {
        return brandJpaRepository.softDeleteById(id);
    }
}
