package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;

    @Transactional
    public BrandInfo create(String name, String description) {
        BrandModel brand = brandService.create(name, description);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getForCustomer(Long id) {
        BrandModel brand = brandService.getActive(id);
        return BrandInfo.from(brand);
    }
}
