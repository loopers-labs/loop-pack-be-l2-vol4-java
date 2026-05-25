package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductListInfoAssembler {

    private final BrandService brandService;
    private final LikeService likeService;

    public PageResult<ProductListInfo> assembleProducts(PageResult<Product> products) {
        return assemble(products, "상품 브랜드 정보가 존재하지 않습니다.");
    }

    public PageResult<ProductListInfo> assembleLikedProducts(PageResult<Product> products) {
        return assemble(products, "좋아요 상품의 브랜드 정보가 존재하지 않습니다.");
    }

    private PageResult<ProductListInfo> assemble(PageResult<Product> products, String missingBrandMessage) {
        Map<Long, Brand> brands = getBrands(products);
        Map<Long, Long> likeCounts = likeService.countProductLikes(productIds(products));

        return products.map(product -> ProductListInfo.from(
            product,
            getBrand(brands, product.getBrandId(), missingBrandMessage),
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

    private Brand getBrand(Map<Long, Brand> brands, Long brandId, String missingBrandMessage) {
        Brand brand = brands.get(brandId);
        if (brand == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, missingBrandMessage);
        }
        return brand;
    }

    private List<Long> productIds(PageResult<Product> products) {
        return products.content().stream()
            .map(Product::getId)
            .toList();
    }
}
