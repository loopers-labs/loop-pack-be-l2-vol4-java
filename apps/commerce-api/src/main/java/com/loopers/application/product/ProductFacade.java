package com.loopers.application.product;

import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;
    private final LikeService likeService;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer quantity) {
        BrandEntity brand = brandService.getBrand(brandId);
        ProductEntity product = productService.createProduct(brandId, name, description, price);
        InventoryEntity inventory = inventoryService.create(product.getId(), quantity);
        return ProductInfo.from(product, brand, inventory);
    }

    public ProductInfo getProduct(Long id) {
        return assembleProductInfo(productService.getProduct(id));
    }

    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productService.getAllProducts(brandId, pageable).map(this::assembleProductInfo);
    }

    @Transactional
    public void updateProduct(Long id, String name, String description, Long price, Integer quantity) {
        productService.updateProduct(id, name, description, price);
        inventoryService.updateQuantity(id, quantity);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        inventoryService.deleteByProduct(id);
        likeService.deleteAllByProduct(id);
    }

    private ProductInfo assembleProductInfo(ProductEntity product) {
        BrandEntity brand = brandService.getBrand(product.getBrandId());
        InventoryEntity inventory = inventoryService.getByProductId(product.getId());
        return ProductInfo.from(product, brand, inventory);
    }
}
