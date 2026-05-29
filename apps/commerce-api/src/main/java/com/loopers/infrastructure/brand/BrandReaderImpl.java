package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandReaderImpl implements BrandReader {

    private final BrandService brandService;

    @Override
    public Brand getBrand(Long brandId) {
        return brandService.getBrand(brandId);
    }
}
