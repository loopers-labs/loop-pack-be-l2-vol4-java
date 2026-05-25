package com.loopers.infrastructure.brand;

import org.springframework.stereotype.Component;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public boolean existsActiveByName(String name) {
        return brandJpaRepository.existsByNameValueAndDeletedAtIsNull(name);
    }
}
