package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandAdminFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandAdminInfo getBrand(Long brandId) {
        BrandModel brand = brandService.getById(brandId);
        long productCount = productService.countByBrandId(brand.getId());
        return BrandAdminInfo.from(brand, productCount);
    }

    public Page<BrandAdminInfo> search(Pageable pageable) {
        return brandService.search(pageable)
            .map(brand -> BrandAdminInfo.from(brand, productService.countByBrandId(brand.getId())));
    }
}
