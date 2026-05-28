package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandApplicationService brandApplicationService;

    public Page<BrandInfo> getBrands(int page, int size) {
        return brandApplicationService.getBrands(PageRequest.of(page, size))
            .map(BrandInfo::from);
    }

    public BrandInfo getBrand(Long brandId) {
        Brand brand = brandApplicationService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    public BrandInfo register(String name) {
        Brand brand = brandApplicationService.register(name);
        return BrandInfo.from(brand);
    }
}
