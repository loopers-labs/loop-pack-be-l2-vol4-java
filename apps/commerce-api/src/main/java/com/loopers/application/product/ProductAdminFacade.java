package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStock;
import com.loopers.domain.stock.ProductStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductAdminFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;

    @Transactional
    public ProductInfo createProduct(CreateProductCommand command) {
        Brand brand = brandService.getBrand(command.brandId());
        Product product = productService.createProduct(
            brand.getId(),
            command.name(),
            command.description(),
            command.price()
        );
        ProductStock productStock = productStockService.createProductStock(product.getId(), command.stockQuantity());
        return ProductInfo.from(product, productStock);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        Product product = productService.getProduct(productId);
        ProductStock productStock = productStockService.getProductStock(product.getId());
        return ProductInfo.from(product, productStock);
    }
}
