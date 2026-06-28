package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    public BrandInfo createBrand(String name, String description, String imageUrl) {
        return BrandInfo.from(brandService.createBrand(name, description, imageUrl));
    }

    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(brandService.getBrand(id));
    }

    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandService.getBrands(pageable).map(BrandInfo::from);
    }

    public BrandInfo updateBrand(Long id, String name, String description, String imageUrl) {
        return BrandInfo.from(brandService.updateBrand(id, name, description, imageUrl));
    }

    @Transactional
    public void deleteBrand(Long id) {
        brandService.getBrand(id);
        List<Long> productIds = productService.getProductIdsByBrandId(id);
        likeService.bulkDeleteByProductIds(productIds);
        productService.bulkSoftDeleteByBrandId(id);
        brandService.softDeleteBrand(id);
    }
}
