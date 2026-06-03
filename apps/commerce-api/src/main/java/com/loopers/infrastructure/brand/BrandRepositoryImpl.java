package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandEntity;
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
    public BrandEntity save(BrandEntity brand) {
        return BrandMapper.toDomain(brandJpaRepository.save(BrandMapper.toJpaEntity(brand)));
    }

    @Override
    public Optional<BrandEntity> findById(Long id) {
        return brandJpaRepository.findById(id)
                .map(BrandMapper::toDomain)
                .filter(b -> !b.isDeleted());
    }

    @Override
    public Page<BrandEntity> findAll(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(BrandMapper::toDomain);
    }

    @Override
    public Optional<BrandEntity> findByName(String name) {
        return brandJpaRepository.findByNameAndDeletedAtIsNull(name)
                .map(BrandMapper::toDomain);
    }
}
