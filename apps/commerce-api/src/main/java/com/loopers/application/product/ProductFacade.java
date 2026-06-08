package com.loopers.application.product;

import com.loopers.application.support.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    public PageResult<ProductInfo> getProducts(Long brandId, String sortValue, int page, int size) {
        ProductSort sort = ProductSort.from(sortValue);
        List<ProductInfo> items = productService.getProducts(brandId, sort, page, size).stream()
            .map(product -> {
                Brand brand = brandService.getBrand(product.getBrandId());
                return ProductInfo.from(product, brand);
            })
            .toList();
        long totalElements = productService.countProducts(brandId);
        return new PageResult<>(items, page, size, totalElements);
    }
}
