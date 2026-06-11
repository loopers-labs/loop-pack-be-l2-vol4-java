package com.loopers.domain.product;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final com.loopers.domain.like.LikeRepository likeRepository;

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getProductsByIds(List<Long> ids) {
        return productRepository.findByIds(ids);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ProductModel> getProducts(Long brandId, String sort, org.springframework.data.domain.Pageable pageable) {
        return productRepository.findAll(brandId, sort, pageable);
    }

    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        ProductModel product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        
        com.loopers.domain.brand.BrandModel brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        int realTimeLikeCount = likeRepository.countByProductId(productId);

        return new ProductDetail(
                product.getId(),
                product.getName(),
                product.getPrice(),
                brand.getId(),
                brand.getName(),
                realTimeLikeCount,
                product.getStock() != null ? product.getStock().getQuantity() : 0
        );
    }

    @Transactional
    public void deleteProductsByBrand(Long brandId) {
        productRepository.deleteByBrandId(brandId);
    }

    @Transactional
    public void increaseLikeCount(Long productId) {
        ProductModel product = getProduct(productId);
        product.increaseLikeCount();
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        ProductModel product = getProduct(productId);
        product.decreaseLikeCount();
    }
}
