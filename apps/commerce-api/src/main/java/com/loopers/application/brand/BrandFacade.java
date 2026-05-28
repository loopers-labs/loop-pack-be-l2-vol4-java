package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandApplicationService brandApplicationService;

    public BrandInfo getBrand(Long brandId) {
        Brand brand = brandApplicationService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    public BrandInfo register(String name) {
        Brand brand = brandApplicationService.register(name);
        return BrandInfo.from(brand);
    }
}
