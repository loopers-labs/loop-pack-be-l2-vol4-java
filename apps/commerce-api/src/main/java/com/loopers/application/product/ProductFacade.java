package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock, String imageUrl) {
        brandService.getBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, price, stock, imageUrl);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock, String imageUrl) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock, imageUrl);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
