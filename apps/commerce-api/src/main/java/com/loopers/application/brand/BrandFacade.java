package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo createBrand(String name, String description) {
        BrandModel brand = brandService.createBrand(name, description);
        return BrandInfo.from(brand);
    }

    public BrandInfo getBrand(Long brandId) {
        BrandModel brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    public List<BrandInfo> getBrands(int page, int size) {
        return brandService.getBrands(page, size).stream()
            .map(BrandInfo::from)
            .toList();
    }

    public BrandInfo updateBrand(Long brandId, String name, String description) {
        BrandModel brand = brandService.updateBrand(brandId, name, description);
        return BrandInfo.from(brand);
    }

    public void deleteBrand(Long brandId) {
        brandService.deleteBrand(brandId);
    }
}
