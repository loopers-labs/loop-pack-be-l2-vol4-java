package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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
    public Optional<BrandModel> findById(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public Page<BrandModel> findAll(Pageable pageable) {
        return brandJpaRepository.findAll(pageable);
    }

    @Override
    public void delete(Long id) {
        brandJpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return brandJpaRepository.existsById(id);
    }
}
