package com.loopers.application.like;

import com.loopers.application.product.ProductCacheRepository;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductLikeCountRepository;
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
    private final ProductCacheRepository productCacheRepository;
    private final ProductLikeCountRepository productLikeCountRepository;

    @Transactional
    public void likeProduct(String userLoginId, Long productId) {
        Product product = productService.getProduct(productId);
        boolean created = productLikeService.likeProduct(userLoginId, productId);
        if (created) {
            if (!productLikeCountRepository.increase(productId, product.getLikeCount())) {
                increaseLikeCountInDb(productId);
            }
            productCacheRepository.evictProduct(productId);
            productCacheRepository.evictProductLists();
        }
    }

    @Transactional
    public void unlikeProduct(String userLoginId, Long productId) {
        Product product = productService.getProduct(productId);
        boolean deleted = productLikeService.unlikeProduct(userLoginId, productId);
        if (deleted) {
            if (!productLikeCountRepository.decrease(productId, product.getLikeCount())) {
                decreaseLikeCountInDb(productId);
            }
            productCacheRepository.evictProduct(productId);
            productCacheRepository.evictProductLists();
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
            .map(this::applyLatestLikeCount)
            .toList();
    }

    private ProductInfo applyLatestLikeCount(ProductInfo productInfo) {
        return productLikeCountRepository.get(productInfo.id())
            .map(productInfo::withLikeCount)
            .orElse(productInfo);
    }

    private void increaseLikeCountInDb(Long productId) {
        Product product = getProductForUpdate(productId);
        product.increaseLikeCount();
        productService.saveProducts(List.of(product));
    }

    private void decreaseLikeCountInDb(Long productId) {
        Product product = getProductForUpdate(productId);
        product.decreaseLikeCount();
        productService.saveProducts(List.of(product));
    }

    private Product getProductForUpdate(Long productId) {
        return productService.findProductsByIdsForUpdate(List.of(productId)).stream()
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
    }
}
