package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
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
    public Page<BrandModel> findAllNotDeleted(Pageable pageable) {
        return brandJpaRepository.findByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<BrandModel> findByNameContainingAndNotDeleted(String keyword, Pageable pageable) {
        return brandJpaRepository.findByNameValueContainingAndDeletedAtIsNull(keyword, pageable);
    }
}