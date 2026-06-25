package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockService;
import com.loopers.support.cache.CacheClient;
import com.loopers.support.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ProductFacade {

    private static final String PRODUCT_LIST_KEY_PREFIX = "commerce:product:list:v1:";
    private static final Duration PRODUCT_LIST_TTL = Duration.ofSeconds(30);

    private final ProductService productService;
    private final ProductStockService productStockService;
    private final BrandService brandService;
    private final CacheClient cacheClient;

    public Page<ProductSummaryInfo> getProducts(Long brandId, ProductStatus status, ProductSortType sort, Pageable pageable) {
        ProductStatus effectiveStatus = status == null ? ProductStatus.ON_SALE : status;

        String cacheKey = productListKey(brandId, effectiveStatus, sort, pageable);
        ProductSummaryPage cached = cacheClient.find(cacheKey, ProductSummaryPage.class)
                                               .orElse(null);
        if (cached != null) {
            return cached.toPage(pageable);
        }

        Page<ProductSummaryInfo> products = productService.getProducts(brandId, effectiveStatus, sort, pageable)
                                                          .map(ProductSummaryInfo::from);
        
        cacheClient.save(cacheKey, ProductSummaryPage.from(products), PRODUCT_LIST_TTL);
        return products;
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL, key = "#productId")
    public ProductDetailInfo getProductDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductDetailInfo.from(product, brand);
    }

    // TODO: 관리자 기능으로 변경될 것
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock, ProductStatus status) {
        ProductModel product = productService.createProduct(brandId, name, description, price, stock, status);
        ProductStockModel stockModel = productStockService.getStock(product.getId());
        return ProductInfo.from(product, stockModel);
    }

    // TODO: 관리자 기능으로 변경될 것
    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        ProductStockModel stockModel = productStockService.getStock(id);
        return ProductInfo.from(product, stockModel);
    }

    // TODO: 관리자 기능으로 변경될 것
    public ProductInfo updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock, ProductStatus status) {
        ProductModel product = productService.updateProduct(id, brandId, name, description, price, stock, status);
        ProductStockModel stockModel = productStockService.getStock(id);
        return ProductInfo.from(product, stockModel);
    }

    // TODO: 관리자 기능으로 변경될 것
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

    private String productListKey(Long brandId, ProductStatus status, ProductSortType sort, Pageable pageable) {
        return PRODUCT_LIST_KEY_PREFIX
                + "status:" + status.name()
                + ":brand:" + (brandId == null ? "all" : brandId)
                + ":sort:" + (sort == null ? "default" : sort.name())
                + ":page:" + pageable.getPageNumber()
                + ":size:" + pageable.getPageSize();
    }
}