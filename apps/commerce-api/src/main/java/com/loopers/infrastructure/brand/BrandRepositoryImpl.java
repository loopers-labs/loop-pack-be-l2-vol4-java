package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;
    private final BrandMapper brandMapper;

    @Override
    public Optional<Brand> find(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(brandMapper::toDomain);
    }

    @Override
    public List<Brand> findAll() {
        return brandJpaRepository.findAllByDeletedAtIsNull()
                .stream()
                .map(brandMapper::toDomain)
                .toList();
    }

    @Override
    public List<Brand> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
                .stream()
                .map(brandMapper::toDomain)
                .toList();
    }

    @Override
    public Brand save(Brand brand) {
        BrandJpaEntity entity = brandMapper.toJpaEntity(brand);
        BrandJpaEntity saved = brandJpaRepository.save(entity);
        return brandMapper.toDomain(saved);
    }

    @Override
    public void update(Brand brand) {
        BrandJpaEntity managed = brandJpaRepository.findByIdAndDeletedAtIsNull(brand.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        managed.update(brand.getName(), brand.getDescription());
        if (brand.isDeleted() && managed.getDeletedAt() == null) {
            managed.delete();
        }
    }
}
