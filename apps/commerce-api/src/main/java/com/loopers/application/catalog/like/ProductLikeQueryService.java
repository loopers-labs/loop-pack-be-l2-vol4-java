package com.loopers.application.catalog.like;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductLikeQueryService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public PageResult<ProductLikeResult> getMyLikes(ProductLikeQuery.MyLikes query) {
        long totalElements = productLikeRepository.countByUserId(query.userId());
        var productLikes = productLikeRepository.findByUserId(query.userId(), query.page(), query.size());
        Map<Long, Product> products = getProducts(productLikes.stream().map(productLike -> productLike.getProductId()).toList());
        Map<Long, Brand> brands = getBrands(products.values().stream().map(Product::getBrandId).toList());

        List<ProductLikeResult> items = productLikes.stream()
            .map(productLike -> toResult(productLike.getProductId(), products, brands))
            .toList();

        return PageResult.of(items, query.page(), query.size(), totalElements);
    }

    private ProductLikeResult toResult(Long productId, Map<Long, Product> products, Map<Long, Brand> brands) {
        Product product = products.get(productId);
        if (product == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }

        Brand brand = brands.get(product.getBrandId());
        if (brand == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다.");
        }

        return ProductLikeResult.from(product, brand, true);
    }

    private Map<Long, Product> getProducts(Collection<Long> productIds) {
        return productRepository.findAllByIds(productIds.stream().distinct().toList())
            .stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Map<Long, Brand> getBrands(Collection<Long> brandIds) {
        return brandRepository.findAllByIds(brandIds.stream().distinct().toList())
            .stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }
}
