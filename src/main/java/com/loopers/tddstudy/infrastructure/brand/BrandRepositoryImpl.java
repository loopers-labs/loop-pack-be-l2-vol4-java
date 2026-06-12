package com.loopers.tddstudy.infrastructure.brand;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository jpaRepository;

    public BrandRepositoryImpl(BrandJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Brand save(Brand brand) {
        return jpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Brand> findAll() {
        return jpaRepository.findAll();
    }
}
