package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private static final long NO_LIKES = 0L;

    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public ProductDetailInfo getProduct(Long productId) {
        Product product = productService.getProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductDetailInfo.from(product, brand, NO_LIKES);
    }
}
