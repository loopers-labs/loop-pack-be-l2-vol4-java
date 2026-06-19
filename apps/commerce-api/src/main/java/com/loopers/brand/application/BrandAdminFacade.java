package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.application.ProductDetailViewInvalidator;
import com.loopers.product.domain.ProductService;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandAdminFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final ProductDetailViewInvalidator productDetailViewInvalidator;

    public BrandInfo createBrand(CreateBrandCommand command) {
        Brand brand = brandService.createBrand(command.name(), command.description());
        return BrandInfo.from(brand);
    }

    public BrandInfo getBrand(Long brandId) {
        Brand brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    public PageResult<BrandInfo> getBrands(int page, int size) {
        return brandService.getBrands(new PageQuery(page, size))
            .map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo updateBrand(UpdateBrandCommand command) {
        Brand brand = brandService.updateBrand(command.brandId(), command.name(), command.description());
        productDetailViewInvalidator.invalidateAll(productService.getActiveProductIdsByBrandId(command.brandId()));
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        brandService.deleteBrand(brandId);
        productDetailViewInvalidator.invalidateAll(productService.deleteProductsByBrandId(brandId));
    }
}
