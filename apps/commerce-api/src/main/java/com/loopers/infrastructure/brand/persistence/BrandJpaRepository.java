package com.loopers.infrastructure.brand.persistence;

import com.loopers.domain.brand.model.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    boolean existsByName(String name);
    List<Brand> findAllByIdIn(List<Long> ids);
}
