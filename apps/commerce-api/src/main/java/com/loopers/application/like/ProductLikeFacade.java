package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductCacheService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductBrandProcessService productBrandProcessService;
    private final ProductCacheService productCacheService;

    @Transactional
    public void likeProduct(String userLoginId, Long productId) {
        Product product = getProductForUpdate(productId);
        boolean created = productLikeService.likeProduct(userLoginId, product);
        if (created) {
            productService.saveProducts(List.of(product));
            productCacheService.evictProduct(productId);
            productCacheService.evictProductLists();
        }
    }

    @Transactional
    public void unlikeProduct(String userLoginId, Long productId) {
        Product product = getProductForUpdate(productId);
        boolean deleted = productLikeService.unlikeProduct(userLoginId, product);
        if (deleted) {
            productService.saveProducts(List.of(product));
            productCacheService.evictProduct(productId);
            productCacheService.evictProductLists();
        }
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(String userLoginId) {
        List<ProductLike> productLikes = productLikeService.getProductLikes(userLoginId);
        List<Long> productIds = productLikes.stream()
            .map(ProductLike::getProductId)
            .distinct()
            .toList();
        List<Product> products = productService.findProductsByIds(productIds);
        List<Product> likedProducts = productLikeService.getLikedProducts(productLikes, products);
        List<Long> brandIds = productBrandProcessService.getBrandIds(likedProducts);
        List<Brand> brands = brandService.getBrandsByIds(brandIds);
        return productBrandProcessService.getProductDetailViews(likedProducts, brands).stream()
            .map(ProductInfo::from)
            .toList();
    }

    private Product getProductForUpdate(Long productId) {
        return productService.findProductsByIdsForUpdate(List.of(productId)).stream()
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
    }
}
