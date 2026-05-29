package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(brandService.getBrand(id));
    }
}
