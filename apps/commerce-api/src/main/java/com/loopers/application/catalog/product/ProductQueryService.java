package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductLikeRepository productLikeRepository;
    private final ProductCacheRepository productCacheRepository;

    @Transactional(readOnly = true)
    public ProductResult getOnSaleProduct(Long productId, String userId) {
        ProductResult product = productCacheRepository.getDetail(productId)
            .orElseGet(() -> cacheOnSaleProduct(productId));
        return product.withLiked(isLiked(userId, productId));
    }

    @Transactional(readOnly = true)
    public PageResult<ProductResult> searchOnSaleProducts(ProductQuery.Search query) {
        var condition = query.toCondition();
        PageResult<ProductResult> products = productCacheRepository.getList(condition)
            .orElseGet(() -> cacheOnSaleProducts(condition));
        return withLiked(products, query.userId());
    }

    @Transactional(readOnly = true)
    public ProductResult getProduct(Long productId) {
        Product product = productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        Brand brand = getBrand(product.getBrandId());

        return ProductResult.from(product, brand);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductResult> searchProducts(ProductQuery.AdminSearch query) {
        return searchProducts(query.toCondition(), null);
    }

    private PageResult<ProductResult> searchProducts(ProductSearchCondition condition, String userId) {
        List<Product> products = productRepository.search(condition);
        Map<Long, Brand> brands = getBrands(products.stream().map(Product::getBrandId).toList());
        Set<Long> likedProductIds = getLikedProductIds(userId, products);
        long totalElements = productRepository.count(condition);

        List<ProductResult> items = products
            .stream()
            .map(product -> ProductResult.from(
                product,
                getBrandFrom(brands, product.getBrandId()),
                likedProductIds.contains(product.getId())
            ))
            .toList();

        return PageResult.of(items, condition.page(), condition.size(), totalElements);
    }

    private ProductResult cacheOnSaleProduct(Long productId) {
        Product product = productRepository.findOnSale(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        Brand brand = getBrand(product.getBrandId());
        ProductResult result = ProductResult.from(product, brand);
        productCacheRepository.putDetail(productId, result);
        return result;
    }

    private PageResult<ProductResult> cacheOnSaleProducts(ProductSearchCondition condition) {
        PageResult<ProductResult> result = searchProducts(condition, null);
        productCacheRepository.putList(condition, result);
        return result;
    }

    private PageResult<ProductResult> withLiked(PageResult<ProductResult> products, String userId) {
        if (userId == null || userId.isBlank() || products.items().isEmpty()) {
            return products;
        }

        Set<Long> likedProductIds = productLikeRepository.findLikedProductIds(
            userId,
            products.items().stream().map(ProductResult::id).distinct().toList()
        );
        return new PageResult<>(
            products.items()
                .stream()
                .map(product -> product.withLiked(likedProductIds.contains(product.id())))
                .toList(),
            products.page(),
            products.size(),
            products.totalElements(),
            products.totalPages(),
            products.hasNext()
        );
    }

    private Brand getBrand(Long brandId) {
        return brandRepository.find(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
    }

    private Map<Long, Brand> getBrands(Collection<Long> brandIds) {
        return brandRepository.findAllByIds(brandIds.stream().distinct().toList())
            .stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    private Brand getBrandFrom(Map<Long, Brand> brands, Long brandId) {
        Brand brand = brands.get(brandId);
        if (brand == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다.");
        }

        return brand;
    }

    private boolean isLiked(String userId, Long productId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }

        return productLikeRepository.exists(userId, productId);
    }

    private Set<Long> getLikedProductIds(String userId, List<Product> products) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }

        return productLikeRepository.findLikedProductIds(
            userId,
            products.stream().map(Product::getId).distinct().toList()
        );
    }
}
