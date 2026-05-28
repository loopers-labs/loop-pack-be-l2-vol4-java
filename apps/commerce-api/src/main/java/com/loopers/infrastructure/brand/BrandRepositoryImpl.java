package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        BrandJpaEntity brandJpaEntity = brand.getId() == null
            ? BrandJpaEntity.from(brand)
            : brandJpaRepository.findById(brand.getId())
                .map(existingBrand -> {
                    existingBrand.update(brand);
                    return existingBrand;
                })
                .orElseGet(() -> BrandJpaEntity.from(brand));

        return brandJpaRepository.save(brandJpaEntity).toDomain();
    }

    @Override
    public Optional<Brand> find(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(BrandJpaEntity::toDomain);
    }

    @Override
    public List<Brand> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
            .map(BrandJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        return brandJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(page, size)).stream()
            .map(BrandJpaEntity::toDomain)
            .toList();
    }
}
