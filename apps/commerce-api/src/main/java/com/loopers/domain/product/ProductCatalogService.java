package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductCatalogService {

    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return new ProductDetail(product, brand);
    }

    @Transactional(readOnly = true)
    public List<ProductDetail> getProductDetails() {
        return getProductDetails(ProductSort.LATEST);
    }

    @Transactional(readOnly = true)
    public List<ProductDetail> getProductDetails(ProductSort sort) {
        return productService.getAllProducts(sort).stream()
            .map(product -> new ProductDetail(product, brandService.getBrand(product.getBrandId())))
            .toList();
    }
}
