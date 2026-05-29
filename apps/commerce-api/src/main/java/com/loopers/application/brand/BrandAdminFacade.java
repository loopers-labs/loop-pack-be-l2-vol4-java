package com.loopers.application.brand;

import com.loopers.domain.brand.BrandAdminService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandAdminFacade {

    private final BrandAdminService brandAdminService;
    private final ProductService productService;

    @Transactional
    public void deleteBrand(Long brandId) {
        brandAdminService.deleteBrand(brandId);
        productService.deleteProductsByBrand(brandId);
    }
}
