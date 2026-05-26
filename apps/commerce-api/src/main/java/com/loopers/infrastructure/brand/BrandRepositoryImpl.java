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
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> find(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        return brandJpaRepository.findAll(PageRequest.of(page, size)).getContent();
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && brandJpaRepository.existsById(id);
    }

    @Override
    public void delete(Long id) {
        brandJpaRepository.deleteById(id);
    }
}
