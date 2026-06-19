package com.loopers.application.product;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private static final String CACHE_KEY = "#sort";
    private static final String CACHEABLE_CONDITION =
            "#brandId == null and #pageable.pageNumber == 0 and #pageable.pageSize == 20";
    private static final String NOT_LIKES_DESC_CONDITION =
            CACHEABLE_CONDITION + " and #sort.name() != 'LIKES_DESC'";
    private static final String LIKES_DESC_CONDITION =
            CACHEABLE_CONDITION + " and #sort.name() == 'LIKES_DESC'";

    private final ProductService productService;
    private final ProductStockService productStockService;
    private final BrandService brandService;

    @Caching(cacheable = {
            @Cacheable(cacheNames = RedisConfig.PRODUCT_LIST_LATEST_PRICE_CACHE, condition = NOT_LIKES_DESC_CONDITION, key = CACHE_KEY),
            @Cacheable(cacheNames = RedisConfig.PRODUCT_LIST_LIKES_CACHE, condition = LIKES_DESC_CONDITION, key = CACHE_KEY)
    })
    public ProductPageResult<ProductInfo> getProducts(Long brandId, ProductSortType sort, Pageable pageable) {
        Page<ProductModel> products = productService.getList(brandId, sort, pageable);
        Map<Long, BrandModel> brandMap = batchBrands(products.getContent());
        List<ProductInfo> content = products.getContent().stream()
                .map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId()), p.getLikeCount()))
                .toList();
        return new ProductPageResult<>(content, products.getTotalElements());
    }

    public ProductInfo getProduct(Long productId) {
        ProductModel product = productService.get(productId);
        BrandModel brand = brandService.get(product.getBrandId());
        List<ProductStockModel> stocks = productStockService.findAllByProductId(productId);
        return ProductInfo.from(product, brand, product.getLikeCount(), stocks);
    }

    public Page<ProductInfo> getAdminProducts(Long brandId, Pageable pageable) {
        Page<ProductModel> products = productService.getAdminList(brandId, pageable);
        Map<Long, BrandModel> brandMap = batchBrands(products.getContent());
        return products.map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId()), p.getLikeCount()));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LATEST_PRICE_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LIKES_CACHE, allEntries = true)
    })
    @Transactional
    public ProductInfo registerProduct(Long brandId, String name, Long price, Integer quantity) {
        BrandModel brand = brandService.get(brandId);
        ProductModel product = productService.create(brand.getId(), new ProductName(name));
        ProductStockModel stock = productStockService.addStock(product, new Price(price), quantity);
        product.updateMinPrice(price);
        return ProductInfo.from(product, brand, 0L, List.of(stock));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LATEST_PRICE_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LIKES_CACHE, allEntries = true)
    })
    @Transactional
    public ProductInfo updateProduct(Long productId, String name, Long stockId, Long price, Integer stockQuantity) {
        ProductModel product = name != null
                ? productService.update(productId, new ProductName(name))
                : productService.get(productId);
        if (stockId != null) {
            productStockService.updateStock(productId, stockId, price, stockQuantity);
        }
        BrandModel brand = brandService.get(product.getBrandId());
        List<ProductStockModel> stocks = productStockService.findAllByProductId(productId);
        if (price != null) {
            Long newMinPrice = stocks.stream()
                    .map(s -> s.getPrice().getValue())
                    .min(Long::compareTo)
                    .orElse(null);
            productService.updateMinPrice(productId, newMinPrice);
        }
        return ProductInfo.from(product, brand, product.getLikeCount(), stocks);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LATEST_PRICE_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LIKES_CACHE, allEntries = true)
    })
    public void deleteProduct(Long productId) {
        productService.delete(productId);
    }

    private Map<Long, BrandModel> batchBrands(List<ProductModel> products) {
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        return brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getId, b -> b));
    }
}
