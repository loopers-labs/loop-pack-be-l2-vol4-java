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
    private final ProductListCacheRepository productListCacheRepository;

    public ProductInfo getProduct(Long productId) {
        ProductDetailCache detail = productCacheRepository.find(productId)
            .orElseGet(() -> {
                ProductDetailCache loaded = productApplicationService.getProductDetailForCache(productId);
                productCacheRepository.save(productId, loaded);
                return loaded;
            });
        int stockQuantity = productApplicationService.getStockQuantity(productId);
        return ProductInfo.of(detail, stockQuantity);
    }

    public Page<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        return productListCacheRepository.find(brandId, sort, page, size)
            .map(ProductListCache::toPage)
            .orElseGet(() -> {
                Page<ProductInfo> result = productApplicationService.getProducts(brandId, ProductSort.from(sort), page, size);
                productListCacheRepository.save(brandId, sort, page, size, ProductListCache.from(result));
                return result;
            });
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
