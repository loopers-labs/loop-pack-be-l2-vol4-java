package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.QProductStockModel;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ProductStockModel save(ProductStockModel stock) {
        return productStockJpaRepository.save(stock);
    }

    @Override
    public Optional<ProductStockModel> findById(Long id) {
        return productStockJpaRepository.findById(id);
    }

    @Override
    public List<ProductStockModel> findAllByProductId(Long productId) {
        QProductStockModel stock = QProductStockModel.productStockModel;
        return queryFactory
                .selectFrom(stock)
                .where(stock.product.id.eq(productId))
                .fetch();
    }
}
