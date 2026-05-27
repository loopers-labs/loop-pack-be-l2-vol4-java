package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.ProductLikeModel;
import com.loopers.domain.like.ProductLikeRepository;
import com.loopers.domain.like.ProductLikeResult;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeRepository productLikeRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductLikeService productLikeService = new ProductLikeService();

    @Transactional
    public void likeProduct(String userLoginId, Long productId) {
        ProductModel product = getProduct(productId);
        Optional<ProductLikeModel> existingLike = productLikeRepository.find(userLoginId, productId);
        ProductLikeResult result = productLikeService.likeProduct(userLoginId, productId, product, existingLike);

        if (result.created()) {
            productLikeRepository.save(result.productLike());
            productRepository.save(product);
        }
    }

    @Transactional
    public void unlikeProduct(String userLoginId, Long productId) {
        Optional<ProductLikeModel> existingLike = productLikeRepository.find(userLoginId, productId);
        if (existingLike.isEmpty()) {
            return;
        }

        ProductModel product = getProduct(productId);
        boolean deleted = productLikeService.unlikeProduct(product, existingLike);
        if (deleted) {
            productLikeRepository.delete(existingLike.get());
            productRepository.save(product);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(String userLoginId) {
        List<ProductLikeModel> productLikes = productLikeRepository.findAllByUserLoginId(userLoginId);
        Map<Long, ProductModel> productsById = productLikes.stream()
            .map(ProductLikeModel::getProductId)
            .distinct()
            .map(productRepository::find)
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        Map<Long, BrandModel> brandsById = productsById.values().stream()
            .map(ProductModel::getBrandId)
            .distinct()
            .map(this::getBrand)
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));

        List<ProductDetail> productDetails = productLikeService.getLikedProductDetails(
            productLikes,
            productsById,
            brandsById
        );
        return productDetails.stream()
            .map(ProductInfo::from)
            .toList();
    }

    private ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    private BrandModel getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
