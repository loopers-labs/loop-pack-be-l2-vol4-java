package com.loopers.application.brand;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @Transactional
    public void deleteBrand(Long id) {
        brandService.deleteBrand(id);
        productService.deleteAllByBrandId(id);
    }
}
