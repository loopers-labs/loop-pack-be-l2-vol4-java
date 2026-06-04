package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.stereotype.Component;

@Component
public class BrandMapper {

    public Brand toDomain(BrandJpaEntity entity) {
        return Brand.restore(
                entity.getId(),
                entity.getName(),
                entity.getDescription()
        );
    }

    public BrandJpaEntity toJpaEntity(Brand domain) {
        return BrandJpaEntity.of(domain.getName(), domain.getDescription());
    }
}
