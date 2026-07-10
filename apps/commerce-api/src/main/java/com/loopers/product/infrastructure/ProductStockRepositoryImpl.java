package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;

    @Override
    public ProductStock save(ProductStock stock) {
        return productStockJpaRepository.save(stock);
    }

    @Override
    public Optional<ProductStock> findByProductId(Long productId) {
        return productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId);
    }

    @Override
    public List<ProductStock> findAllByProductIdIn(List<Long> productIds) {
        return productStockJpaRepository.findAllByProductIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public Optional<ProductStock> findByProductIdForUpdate(Long productId) {
        return productStockJpaRepository.findByProductIdForUpdate(productId);
    }

    // @Modifying UPDATE 는 트랜잭션이 필요하다. 독립 호출 시 자체 트랜잭션으로 실행되고,
    // 상위 @Transactional(주문 생성) 안에서는 REQUIRED 전파로 합류해 실패 시 함께 롤백된다.
    @Override
    @Transactional
    public int decreaseStock(Long productId, int quantity) {
        return productStockJpaRepository.decreaseStock(productId, quantity);
    }

    @Override
    public int softDeleteByBrandId(Long brandId) {
        return productStockJpaRepository.softDeleteByBrandId(brandId);
    }
}
