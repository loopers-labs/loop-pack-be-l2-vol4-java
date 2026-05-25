package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductService;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductListInfoAssembler productListInfoAssembler;

    @Transactional(readOnly = true)
    public PageResult<ProductListInfo> getProducts(int page, int size, Long brandId, String sort) {
        PageResult<Product> products = productService.getVisibleProducts(
            new PageQuery(page, size),
            brandId,
            ProductSort.from(sort)
        );
        return productListInfoAssembler.assembleProducts(products);
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getProduct(Long productId) {
        Product product = productService.getVisibleProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        long likeCount = likeService.countProductLikes(product.getId());
        return ProductDetailInfo.from(product, brand, likeCount);
    }
}
