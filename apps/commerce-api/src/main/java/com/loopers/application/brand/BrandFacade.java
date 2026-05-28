package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        return BrandInfo.from(brandService.createBrand(name, description));
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(brandService.getBrand(id));
    }

    @Transactional(readOnly = true)
    public List<BrandInfo> getBrands(Integer page, Integer size) {
        return brandService.getBrands(page, size).stream()
            .map(BrandInfo::from)
            .toList();
    }

    @Transactional
    public BrandInfo updateBrand(Long id, String name, String description) {
        Brand brand = brandService.updateBrand(id, name, description);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        brandService.deleteBrand(id);
    }
}
