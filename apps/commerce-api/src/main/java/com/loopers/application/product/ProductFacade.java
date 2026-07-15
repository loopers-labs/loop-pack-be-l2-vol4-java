package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.cache.RedisCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final RedisCacheRepository cacheRepository;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        ProductModel product = productService.createProduct(name, description, price, stock, brandId);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        String key = ProductCachePolicy.detailKey(id);
        Optional<ProductInfo> cached = cacheRepository.find(key, ProductInfo.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        ProductInfo info = ProductInfo.from(productService.getProduct(id));
        cacheRepository.save(key, info, ProductCachePolicy.detailTtl());
        return info;
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductPageInfo searchProducts(ProductSearchCondition condition) {
        // 조회가 집중되는 앞쪽 페이지만 캐시해 키 공간을 줄인다. 깊은 페이지는 인덱스로 감당한다.
        if (!ProductCachePolicy.isListCacheable(condition)) {
            return ProductPageInfo.from(productService.searchProducts(condition));
        }

        String key = ProductCachePolicy.listKey(condition);
        Optional<ProductPageInfo> cached = cacheRepository.find(key, ProductPageInfo.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        ProductPageInfo pageInfo = ProductPageInfo.from(productService.searchProducts(condition));
        cacheRepository.save(key, pageInfo, ProductCachePolicy.LIST_TTL);
        return pageInfo;
    }

    public ProductInfo updateProduct(
        Long id, String name, String description, Long price, Integer stock, Long brandId) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock, brandId);
        // 트랜잭션(Service) 커밋 이후에만 무효화해 롤백-무효화 순서 역전을 방지한다.
        cacheRepository.evict(ProductCachePolicy.detailKey(id));
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        cacheRepository.evict(ProductCachePolicy.detailKey(id));
    }
}
