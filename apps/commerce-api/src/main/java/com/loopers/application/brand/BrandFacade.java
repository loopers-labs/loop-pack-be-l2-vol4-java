package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.inventory.InventoryService;
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
    private final InventoryService inventoryService;
    private final LikeService likeService;

    public BrandInfo getBrand(Long brandId) {
        return BrandInfo.from(brandService.getBrand(brandId));
    }

    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandService.getBrands(pageable).map(BrandInfo::from);
    }

    public BrandInfo createBrand(String name, String description) {
        return BrandInfo.from(brandService.create(name, description));
    }

    public BrandInfo updateBrand(Long brandId, String name, String description) {
        return BrandInfo.from(brandService.update(brandId, name, description));
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        brandService.delete(brandId);
        List<Long> productIds = productService.findIdsByBrand(brandId);
        productService.deleteAll(productIds);
        inventoryService.deleteAllByProducts(productIds);
        likeService.deleteAllByProducts(productIds);
    }
}
