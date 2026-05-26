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

    public BrandInfo createBrand(String name, String description) {
        return BrandInfo.from(brandService.createBrand(name, description));
    }

    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(brandService.getBrand(id));
    }

    public List<BrandInfo> getBrands(int page, int size) {
        return brandService.getBrands(page, size).stream()
            .map(BrandInfo::from)
            .toList();
    }

    public BrandInfo updateBrand(Long id, String name, String description) {
        return BrandInfo.from(brandService.updateBrand(id, name, description));
    }

    /**
     * 브랜드를 삭제하면 해당 브랜드에 속한 상품도 함께 삭제한다.
     */
    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = brandService.getBrand(id); // 존재 여부 확인
        productService.deleteProductsByBrand(brand.getId());
        brandService.deleteBrand(brand.getId());
    }
}
