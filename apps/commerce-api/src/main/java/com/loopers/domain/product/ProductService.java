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
    public ProductModel createProduct(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = new ProductModel(brandId, name, description, imageUrl, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /** 대고객 조회: 활성 상품만 반환. 없거나 비활성이면 NOT_FOUND (01 §7.4, UC-04). */
    @Transactional(readOnly = true)
    public ProductModel getActiveProduct(Long id) {
        return productRepository.find(id)
            .filter(ProductModel::isActive)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllProducts() {
        return productRepository.findAll();
    }

    /** 특정 브랜드의 활성 상품 전체 (Brand→Product cascade 전파용 — 01 §7.5). */
    @Transactional(readOnly = true)
    public List<ProductModel> getActiveProductsByBrand(Long brandId) {
        return productRepository.findActiveByBrandId(brandId);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = getProduct(id);
        product.update(name, description, imageUrl, price, stock);
        return productRepository.save(product);
    }

    /** soft delete (01 §7.5). 행은 남기고 deletedAt만 설정 → 조회·신규 좋아요에서 NOT_FOUND로 가려진다. */
    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProduct(id);
        product.delete();
        productRepository.save(product);
    }

    /** 재고 차감 — 활성 상품만. 부족 시 ProductModel/Stock이 CONFLICT (주문 트랜잭션 내 호출). */
    @Transactional
    public void deductStock(Long id, int quantity) {
        ProductModel product = getActiveProduct(id);
        product.deductStock(quantity);
        productRepository.save(product);
    }

    /** 재고 복원 (결제 실패 원복 — 01 §7.6). */
    @Transactional
    public void restoreStock(Long id, int quantity) {
        ProductModel product = getProduct(id);
        product.restoreStock(quantity);
        productRepository.save(product);
    }

    /** 좋아요 수 동기 +1 (01 §7.3, 04 §4.2 — 좋아요 등록과 동일 트랜잭션). */
    @Transactional
    public void increaseLikesCount(Long id) {
        ProductModel product = getProduct(id);
        product.incrementLikesCount();
        productRepository.save(product);
    }

    /** 좋아요 수 동기 -1 (음수 방지는 ProductModel 책임). */
    @Transactional
    public void decreaseLikesCount(Long id) {
        ProductModel product = getProduct(id);
        product.decrementLikesCount();
        productRepository.save(product);
    }
}
