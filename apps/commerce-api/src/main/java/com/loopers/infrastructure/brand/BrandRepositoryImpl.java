package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<BrandModel> find(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<BrandModel> findAll() {
        return brandJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public void delete(Long id) {
        brandJpaRepository.findByIdAndDeletedAtIsNull(id).ifPresent(brand -> {
            brand.delete();
            brandJpaRepository.save(brand);
        });
    }
}