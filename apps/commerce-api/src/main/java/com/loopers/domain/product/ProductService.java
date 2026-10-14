package com.loopers.domain.product;

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

    @Transactional
    public ProductModel createProduct(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {
        ProductModel product = new ProductModel(brandId, name, description, price, stock, imageUrl);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> listProducts(ProductSortType sortType, Long brandId) {
        ProductSortType effectiveSort = sortType == null ? ProductSortType.LATEST : sortType;
        return productRepository.findAll(effectiveSort, brandId);
    }

    /**
     * 단일 상품의 재고를 차감한다. 도메인 메서드가 음수 방지/상태 전이를 담당한다.
     * 동시성 차단을 위해 비관적 락으로 조회한다.
     */
    @Transactional
    public ProductModel decreaseStock(Long productId, int quantity) {
        ProductModel product = productRepository.findForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        product.decreaseStock(quantity);
        return productRepository.save(product);
    }

    /**
     * 좋아요 카운트 증가. 인프라의 원자적 UPDATE 위임.
     */
    @Transactional
    public void incrementLikeCount(Long productId) {
        productRepository.incrementLikeCount(productId);
    }

    /**
     * 좋아요 카운트 감소.
     */
    @Transactional
    public void decrementLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }
}
