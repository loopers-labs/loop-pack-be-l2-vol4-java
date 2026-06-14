package com.loopers.like.application;

import com.loopers.product.application.ProductListInfo;
import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.like.domain.LikeService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
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
public class LikeFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getProduct(productId);
        likeService.like(userId, productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductListInfo> getMyLikes(GetMyLikesCommand command) {
        if (!command.isOwnUser()) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 좋아요 목록은 조회할 수 없습니다.");
        }

        PageResult<Product> products = productService.getLikedProducts(
            command.userId(),
            new PageQuery(command.page(), command.size())
        );
        Map<Long, Brand> brands = getBrands(products);
        Map<Long, Long> likeCounts = likeService.countProductLikes(productIds(products));

        return products.map(product -> ProductListInfo.from(
            product,
            getBrand(brands, product.getBrandId()),
            likeCounts.getOrDefault(product.getId(), 0L)
        ));
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
            throw new CoreException(ErrorType.INTERNAL_ERROR, "좋아요 상품의 브랜드 정보가 존재하지 않습니다.");
        }
        return brand;
    }

    private List<Long> productIds(PageResult<Product> products) {
        return products.content().stream()
            .map(Product::getId)
            .toList();
    }
}
