package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final BrandService brandService;

    @Transactional
    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        if (brandId != null) {
            brandService.getOrThrow(brandRepository.find(brandId));
        }
        ProductModel product = new ProductModel(name, description, price, stock, brandId);
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        return ProductInfo.from(product);
    }

    @Transactional
    public ProductInfo updateProduct(Long productId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        product.update(name, description, price, stock);
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        product.delete();
        productRepository.save(product);
    }
}
