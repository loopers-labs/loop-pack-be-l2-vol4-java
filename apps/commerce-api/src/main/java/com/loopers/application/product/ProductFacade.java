package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final ProductStockService productStockService;
    private final BrandService brandService;

    public Page<ProductSummaryInfo> getProducts(Long brandId, ProductSortType sort, Pageable pageable) {
        Page<ProductModel> products = productService.getProducts(brandId, sort, pageable);
        return products.map(ProductSummaryInfo::from);
    }

    public ProductDetailInfo getProductDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductDetailInfo.from(product, brand);
    }

    // TODO: 관리자 기능으로 변경될 것
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.createProduct(brandId, name, description, price, stock);
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
    public ProductInfo updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, brandId, name, description, price, stock);
        ProductStockModel stockModel = productStockService.getStock(id);
        return ProductInfo.from(product, stockModel);
    }

    // TODO: 관리자 기능으로 변경될 것
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}