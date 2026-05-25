package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @Transactional
    public BrandInfo createBrand(BrandCommand.Create command) {
        Brand brand = brandService.createBrand(command.name(), command.description());
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo updateBrand(BrandCommand.Update command) {
        Brand brand = brandService.updateBrand(command.brandId(), command.name(), command.description());
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        productService.deleteAllByBrandId(id);
        brandService.deleteBrand(id);
    }
}
