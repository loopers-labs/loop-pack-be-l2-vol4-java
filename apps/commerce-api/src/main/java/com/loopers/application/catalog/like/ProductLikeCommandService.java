package com.loopers.application.catalog.like;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLike;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.application.catalog.product.ProductCacheRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductLikeCommandService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductCacheRepository productCacheRepository;

    @Transactional
    public ProductLikeResult like(ProductLikeCommand.Like command) {
        Product product = getProduct(command.productId());
        if (!product.isOnSale()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "판매 가능한 상품만 좋아요를 등록할 수 있습니다.");
        }

        boolean created = productLikeRepository.saveIfAbsent(new ProductLike(command.userId(), command.productId()));
        if (created) {
            productRepository.increaseLikeCount(command.productId());
            evictProductCaches(command.productId());
            product = getProduct(command.productId());
        }

        return ProductLikeResult.from(product, getBrand(product.getBrandId()), true);
    }

    @Transactional
    public ProductLikeResult unlike(ProductLikeCommand.Unlike command) {
        Product product = getProduct(command.productId());
        boolean deleted = productLikeRepository.delete(command.userId(), command.productId());

        if (deleted) {
            productRepository.decreaseLikeCount(command.productId());
            evictProductCaches(command.productId());
            product = getProduct(command.productId());
        }

        return ProductLikeResult.from(product, getBrand(product.getBrandId()), false);
    }

    private Product getProduct(Long productId) {
        return productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
    }

    private Brand getBrand(Long brandId) {
        return brandRepository.find(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
    }

    private void evictProductCaches(Long productId) {
        productCacheRepository.evictDetail(productId);
        productCacheRepository.evictLists();
    }
}
