package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductBrandProcessService productBrandProcessService;
    private final ProductCacheService productCacheService;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        Brand brand = brandService.getBrand(brandId);
        Product product = productService.createProduct(brandId, name, description, price, stock);
        ProductInfo productInfo = ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
        productCacheService.evictProductLists();
        productCacheService.cacheProduct(productInfo);
        return productInfo;
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        return productCacheService.getProduct(id)
            .orElseGet(() -> getProductFromDb(id));
    }

    private ProductInfo getProductFromDb(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        ProductInfo productInfo = ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
        productCacheService.cacheProduct(productInfo);
        return productInfo;
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        if (brandId != null) {
            brandService.validateBrandExists(brandId);
        }

        return productCacheService.getProducts(brandId, sort, page, size)
            .orElseGet(() -> getAllProductsFromDb(brandId, sort, page, size));
    }

    private List<ProductInfo> getAllProductsFromDb(Long brandId, String sort, Integer page, Integer size) {
        List<Product> products = productService.getAllProducts(brandId, sort, page, size);
        List<Long> brandIds = productBrandProcessService.getBrandIds(products);
        List<Brand> brands = brandService.getBrandsByIds(brandIds);
        List<ProductInfo> productInfos = productBrandProcessService.getProductDetailViews(products, brands).stream()
            .map(ProductInfo::from)
            .toList();
        productCacheService.cacheProducts(brandId, sort, page, size, productInfos);
        return productInfos;
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.updateProduct(id, name, description, price, stock);
        Brand brand = brandService.getBrand(product.getBrandId());
        ProductInfo productInfo = ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
        productCacheService.evictProduct(id);
        productCacheService.evictProductLists();
        productCacheService.cacheProduct(productInfo);
        return productInfo;
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        productCacheService.evictProduct(id);
        productCacheService.evictProductLists();
    }
}
