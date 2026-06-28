package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import lombok.RequiredArgsConstructor;
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
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> find(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public List<Brand> findAll() {
        return brandJpaRepository.findAll();
    }

    @Override
    public List<Brand> findAllByIds(Collection<Long> ids) {
        return brandJpaRepository.findAllById(ids);
    }

    @Override
    public boolean existsById(Long id) {
        return brandJpaRepository.existsById(id);
    }
}
