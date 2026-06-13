package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
    public Brand save(Brand domain) {
        if (domain.getId() == null) {
            BrandEntity entity = new BrandEntity(domain.getName(), domain.getDescription());
            return brandJpaRepository.save(entity).toDomain();
        }
        BrandEntity entity = brandJpaRepository.findById(domain.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
        entity.updateFrom(domain);
        return brandJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(BrandEntity::toDomain);
    }

    @Override
    public Optional<Brand> findByIdForUpdate(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNullForUpdate(id)
            .map(BrandEntity::toDomain);
    }

    @Override
    public Page<Brand> findAll(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(pageable)
            .map(BrandEntity::toDomain);
    }
}
