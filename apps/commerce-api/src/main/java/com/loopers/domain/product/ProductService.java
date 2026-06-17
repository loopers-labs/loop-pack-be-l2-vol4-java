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
    public ProductModel createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = new ProductModel(brandId, name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ProductPage searchProducts(Long brandId, String sort, String direction, Integer page, Integer size) {
        ProductSearchCondition condition = ProductSearchCondition.of(brandId, sort, direction, page, size);
        return productRepository.search(condition);
    }

    @Transactional
    public ProductModel updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = getProduct(id);
        product.update(brandId, name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional
    public void deductStock(Long id, int quantity) {
        // 원자적 조건부 UPDATE: 읽기-검사-쓰기를 단일 쿼리로 처리해 고경쟁 상황의 초과판매를 차단한다.
        // 영향받은 행이 0이면 재고가 부족하다는 의미다.
        int updated = productRepository.deductStock(id, quantity);
        if (updated == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
    }

    @Transactional
    public void increaseLikeCount(Long id) {
        // 비정규화된 like_count를 원자적 UPDATE로 증가. 좋아요 insert가 실제로 일어난 경우에만 호출된다.
        productRepository.increaseLikeCount(id);
    }

    @Transactional
    public void decreaseLikeCount(Long id) {
        // like_count > 0 가드가 쿼리에 포함돼 음수로 내려가지 않는다. 좋아요 delete가 실제로 일어난 경우에만 호출된다.
        productRepository.decreaseLikeCount(id);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id); // 존재 여부 확인
        productRepository.delete(id);
    }
}
