package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brand) {
        if (brand.getId() != null) {
            BrandEntity entity = brandJpaRepository.findById(brand.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brand.getId() + "] 브랜드를 찾을 수 없습니다."));
            entity.update(brand.getName(), brand.getDescription());
            return brandJpaRepository.save(entity).toDomain();
        }
        return brandJpaRepository.save(BrandEntity.from(brand)).toDomain();
    }

    @Override
    public Optional<BrandModel> find(Long id) {
        return brandJpaRepository.findById(id).map(BrandEntity::toDomain);
    }

    @Override
    public Page<BrandModel> findAll(Pageable pageable) {
        return brandJpaRepository.findAll(pageable).map(BrandEntity::toDomain);
    }

    @Override
    public Map<Long, BrandModel> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return brandJpaRepository.findAllById(ids).stream()
            .map(BrandEntity::toDomain)
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByName(name);
    }

    @Override
    public void delete(Long id) {
        BrandEntity entity = brandJpaRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
        entity.delete();
        brandJpaRepository.save(entity);
    }
}
