package com.loopers.application.like;

import com.loopers.application.product.ProductListInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
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
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 목록 조회 요청은 비어있을 수 없습니다.");
        }
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
