package com.loopers.domain.product;

import com.loopers.domain.quantity.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /**
     * 좋아요 수 원자적 증감. 모든 동시 요청이 빠짐없이 반영되어야 하므로 한 문장 UPDATE 로 처리한다.
     */
    @Transactional
    public void increaseLikeCount(Long productId) {
        productRepository.increaseLikeCount(productId);
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        productRepository.decreaseLikeCount(productId);
    }

    /**
     * 상품 행에 비관적 락을 걸어 조회한다. 재고 차감처럼 전원 차례 처리가 필요한 경로 전용.
     */
    @Transactional
    public List<Product> getProductsForUpdate(List<Long> ids) {
        List<Product> products = productRepository.findAllByIdForUpdate(ids);
        if (products.size() != ids.size()) {
            Set<Long> foundIds = products.stream().map(Product::getId).collect(Collectors.toSet());
            Long missingId = ids.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse(null);
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + missingId + "] 상품을 찾을 수 없습니다.");
        }
        return products;
    }

    /** 결제 실패 보상: 차감했던 재고를 되돌린다. 동시 보상의 lost update 를 막기 위해 비관적 락으로 읽는다. */
    @Transactional
    public void restoreStock(Long productId, int quantity) {
        Product product = getProductsForUpdate(List.of(productId)).get(0);
        product.restoreStock(new Quantity(quantity));
    }

    @Transactional(readOnly = true)
    public List<Product> getProducts(Long brandId, ProductSort sort, int page, int size) {
        return productRepository.findAll(brandId, sort, page, size);
    }

    @Transactional(readOnly = true)
    public long countProducts(Long brandId) {
        return productRepository.count(brandId);
    }
}
