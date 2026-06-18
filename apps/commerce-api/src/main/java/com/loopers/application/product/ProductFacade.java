package com.loopers.application.product;

import com.loopers.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductApplicationService productApplicationService;
    private final ProductCacheRepository productCacheRepository;

    public ProductInfo getProduct(Long productId) {
        return productCacheRepository.find(productId)
            .orElseGet(() -> {
                ProductInfo info = productApplicationService.getProduct(productId);
                productCacheRepository.save(productId, info);
                return info;
            });
    }

    public Page<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        return productApplicationService.getProducts(brandId, ProductSort.from(sort), page, size);
    }

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, int initialQuantity) {
        Product product = productApplicationService.createProduct(brandId, name, description, price, initialQuantity);
        return ProductInfo.from(product);
    }

    public void updateProduct(Long productId, String name, String description, Long price) {
        productApplicationService.updateProduct(productId, name, description, price);
        productCacheRepository.evict(productId);
    }

    public void deleteProduct(Long productId) {
        productApplicationService.deleteProduct(productId);
        productCacheRepository.evict(productId);
    }
}
