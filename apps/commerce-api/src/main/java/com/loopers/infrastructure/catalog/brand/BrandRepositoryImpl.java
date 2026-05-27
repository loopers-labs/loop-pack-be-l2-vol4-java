package com.loopers.infrastructure.catalog.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        BrandJpaEntity entity = brand.isNew()
            ? BrandJpaEntity.from(brand)
            : brandJpaRepository.findById(brand.getId()).orElseGet(() -> BrandJpaEntity.from(brand));
        entity.apply(brand);
        return brandJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Brand> find(Long id) {
        return brandJpaRepository.findById(id).map(BrandJpaEntity::toDomain);
    }

    @Override
    public Optional<Brand> findActive(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id).map(BrandJpaEntity::toDomain);
    }

    @Override
    public List<Brand> findAllByIds(Collection<Long> ids) {
        return brandJpaRepository.findAllById(ids)
            .stream()
            .map(BrandJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        return brandJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
            .stream()
            .map(BrandJpaEntity::toDomain)
            .toList();
    }

    @Override
    public long countAll() {
        return brandJpaRepository.count();
    }
}
