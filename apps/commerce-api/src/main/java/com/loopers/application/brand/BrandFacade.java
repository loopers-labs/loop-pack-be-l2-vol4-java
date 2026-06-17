package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo createBrand(String name, String description) {
        return BrandInfo.from(brandService.createBrand(name, description));
    }

    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(brandService.getBrand(id));
    }

    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandService.getBrands(pageable).map(BrandInfo::from);
    }

    public BrandInfo updateBrand(Long id, String name, String description) {
        return BrandInfo.from(brandService.updateBrand(id, name, description));
    }

    public void deleteBrand(Long id) {
        brandService.deleteBrand(id);
    }
}
