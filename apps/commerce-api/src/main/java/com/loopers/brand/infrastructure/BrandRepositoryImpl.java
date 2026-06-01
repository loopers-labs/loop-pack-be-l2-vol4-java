package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
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
    public List<BrandModel> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findAllByIds(ids);
    }
}
