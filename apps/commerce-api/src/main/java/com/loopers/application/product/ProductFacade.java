package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStock;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.product.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final ProductStockService productStockService;
    private final BrandReader brandReader;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, int initialStock) {
        ProductModel product = productService.createProduct(brandId, name, description, price);
        ProductStock stock = productStockService.createStock(product.getId(), initialStock);
        Brand brand = brandReader.getBrand(brandId);
        return ProductInfo.of(product, stock, brand);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        ProductStock stock = productStockService.getStock(id);
        Brand brand = brandReader.getBrand(product.getBrandId());
        return ProductInfo.of(product, stock, brand);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(SortType sortType) {
        return productService.getAllProducts(sortType).stream()
            .map(product -> {
                ProductStock stock = productStockService.getStock(product.getId());
                Brand brand = brandReader.getBrand(product.getBrandId());
                return ProductInfo.of(product, stock, brand);
            })
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price) {
        ProductModel product = productService.updateProduct(id, name, description, price);
        ProductStock stock = productStockService.getStock(id);
        Brand brand = brandReader.getBrand(product.getBrandId());
        return ProductInfo.of(product, stock, brand);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
