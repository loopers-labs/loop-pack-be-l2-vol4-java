package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandAdminFacade {

    private final BrandService brandService;

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

    public BrandInfo updateBrand(UpdateBrandCommand command) {
        Brand brand = brandService.updateBrand(command.brandId(), command.name(), command.description());
        return BrandInfo.from(brand);
    }
}
