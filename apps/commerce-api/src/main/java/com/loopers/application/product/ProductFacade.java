package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;

    @Transactional(readOnly = true)
    public PageResult<ProductListInfo> getProducts(int page, int size, Long brandId, String sort) {
        PageResult<Product> products = productService.getVisibleProducts(
            new PageQuery(page, size),
            brandId,
            ProductSort.from(sort)
        );
        Map<Long, Brand> brands = getBrands(products);
        Map<Long, Long> likeCounts = likeService.countProductLikes(productIds(products));

        return products.map(product -> ProductListInfo.from(
            product,
            getBrand(brands, product.getBrandId()),
            likeCounts.getOrDefault(product.getId(), 0L)
        ));
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getProduct(Long productId) {
        Product product = productService.getVisibleProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        long likeCount = likeService.countProductLikes(product.getId());
        return ProductDetailInfo.from(product, brand, likeCount);
    }

    private Map<Long, Brand> getBrands(PageResult<Product> products) {
        return brandService.getBrands(products.content().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList()
            ).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    private Brand getBrand(Map<Long, Brand> brands, Long brandId) {
        Brand brand = brands.get(brandId);
        if (brand == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "상품 브랜드 정보가 존재하지 않습니다.");
        }
        return brand;
    }

    private List<Long> productIds(PageResult<Product> products) {
        return products.content().stream()
            .map(Product::getId)
            .toList();
    }
}
