package com.loopers.brand.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.brand.domain.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final BrandRepository brandRepository;

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        BrandModel brand = new BrandModel(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long brandId) {
        BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo updateBrand(Long brandId, String name, String description) {
        BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
        brand.update(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
        brand.delete();
        brandRepository.save(brand);
    }
}
