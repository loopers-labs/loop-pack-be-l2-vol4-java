package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandInfo createBrand(String name) {
        Brand brand = brandService.createBrand(name);
        return BrandInfo.from(brand);
    }

    public BrandInfo getBrand(Long id) {
        Brand brand = brandService.getBrand(id);
        return BrandInfo.from(brand);
    }

    public List<BrandInfo> getAllBrands() {
        return brandService.getAllBrands().stream()
            .map(BrandInfo::from)
            .toList();
    }

    public BrandInfo updateBrand(Long id, String newName) {
        Brand brand = brandService.updateBrand(id, newName);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        productService.deleteAllByBrandId(id);  // 하위 상품 먼저 soft delete
        brandService.deleteBrand(id);
    }
}
