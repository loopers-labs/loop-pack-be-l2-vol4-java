package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductBrandProcessor;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductBrandProcessor productBrandProcessor;

    public void likeProduct(String userLoginId, Long productId) {
        ProductModel product = getProduct(productId);
        Optional<ProductLikeModel> existingLike = productLikeRepository.find(userLoginId, productId);
        ProductLikeResult result = createLike(userLoginId, productId, product, existingLike);

        if (result.created()) {
            productLikeRepository.save(result.productLike());
            productRepository.save(product);
        }
    }

    public void unlikeProduct(String userLoginId, Long productId) {
        Optional<ProductLikeModel> existingLike = productLikeRepository.find(userLoginId, productId);
        if (existingLike.isEmpty()) {
            return;
        }

        ProductModel product = getProduct(productId);
        boolean deleted = deleteLike(product, existingLike);
        if (deleted) {
            productLikeRepository.delete(existingLike.get());
            productRepository.save(product);
        }
    }

    public List<ProductDetail> getLikedProductDetails(String userLoginId) {
        List<ProductLikeModel> productLikes = productLikeRepository.findAllByUserLoginId(userLoginId);
        List<Long> productIds = productLikes.stream()
            .map(ProductLikeModel::getProductId)
            .distinct()
            .toList();

        List<ProductModel> products = productRepository.findAllByIds(productIds);
        List<ProductModel> likedProducts = getLikedProducts(productLikes, products);
        List<Long> brandIds = productBrandProcessor.getBrandIds(likedProducts);
        List<BrandModel> brands = brandRepository.findAllByIds(brandIds);
        return productBrandProcessor.getProductDetails(likedProducts, brands);
    }

    public ProductLikeResult createLike(
        String userLoginId,
        Long productId,
        ProductModel product,
        Optional<ProductLikeModel> existingLike
    ) {
        if (existingLike.isPresent()) {
            return new ProductLikeResult(existingLike.get(), false);
        }

        ProductLikeModel productLike = new ProductLikeModel(userLoginId, productId);
        product.increaseLikeCount();
        return new ProductLikeResult(productLike, true);
    }

    public boolean deleteLike(ProductModel product, Optional<ProductLikeModel> existingLike) {
        if (existingLike.isEmpty()) {
            return false;
        }
        product.decreaseLikeCount();
        return true;
    }

    public List<ProductModel> getLikedProducts(
        List<ProductLikeModel> productLikes,
        List<ProductModel> products
    ) {
        Map<Long, ProductModel> productsById = products.stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        return productLikes.stream()
            .map(ProductLikeModel::getProductId)
            .distinct()
            .map(productsById::get)
            .filter(Objects::nonNull)
            .toList();
    }

    private ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }
}
