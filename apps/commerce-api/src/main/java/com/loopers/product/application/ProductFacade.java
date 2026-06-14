package com.loopers.product.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.like.domain.LikeService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSort;
import com.loopers.product.domain.ProductService;
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
    private final ProductListQuery productListQuery;

    @Transactional(readOnly = true)
    public PageResult<ProductListInfo> getProducts(int page, int size, Long brandId, String sort) {
        return productListQuery.findVisibleProducts(
            new PageQuery(page, size),
            brandId,
            ProductSort.from(sort)
        );
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getProduct(Long productId) {
        Product product = productService.getVisibleProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        long likeCount = likeService.countProductLikes(product.getId());
        return ProductDetailInfo.from(product, brand, likeCount);
    }
}
