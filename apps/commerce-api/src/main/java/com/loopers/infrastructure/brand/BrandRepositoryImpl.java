package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<BrandModel> find(UUID id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public Optional<BrandModel> findActive(UUID id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByName(name);
    }

    @Override
    public Page<BrandModel> findAll(Pageable pageable) {
        return brandJpaRepository.findAll(pageable);
    }
}
