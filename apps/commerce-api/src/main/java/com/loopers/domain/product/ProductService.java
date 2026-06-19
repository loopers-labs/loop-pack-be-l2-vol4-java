package com.loopers.domain.product;

import com.loopers.domain.shared.Money;
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
    private final ProductLikeStatRepository productLikeStatRepository;

    /**
     * 상품 생성 시 ProductLikeStat 도 0 으로 같이 init 한다.
     * 신규 상품이 좋아요 정렬 쿼리에서 누락되지 않도록 보장하기 위함이다 (좋아요 0 으로 시작).
     * 같은 트랜잭션 안이라 product 와 stat 의 원자성도 같이 보장된다.
     */
    @Transactional
    public Product createProduct(String name, String description, Money price, Integer stock, Long brandId) {
        Product product = productRepository.save(Product.create(name, description, price, stock, brandId));
        productLikeStatRepository.save(ProductLikeStat.init(product.getId(), product.getBrandId()));
        return product;
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /**
     * 비관적 쓰기 락으로 상품을 조회한다. 재고 차감처럼 동시성 충돌이 잦은 쓰기 경로에서만 사용한다.
     * readOnly 가 아니므로 호출자(예: OrderFacade) 의 쓰기 트랜잭션에 합류해야 락이 의미를 갖는다.
     */
    @Transactional
    public Product getProductForUpdate(Long id) {
        return productRepository.findForUpdate(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Product> getProducts(Long brandId, ProductSortType sort, int page, int size) {
        return productRepository.findAll(brandId, sort, page, size);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByIds(List<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    @Transactional
    public Product updateProduct(Long id, String name, String description, Money price, Integer stock) {
        Product product = getProduct(id);
        product.update(name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id); // 존재 여부 확인
        productRepository.delete(id);
    }
}
