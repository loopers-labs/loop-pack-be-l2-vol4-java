package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;

    public Page<BrandInfo> getBrands(String keyword, int page, int size) {
        Page<BrandModel> brands = brandService.getBrands(keyword, page, size);
        return brands.map(BrandInfo::from);
    }
}