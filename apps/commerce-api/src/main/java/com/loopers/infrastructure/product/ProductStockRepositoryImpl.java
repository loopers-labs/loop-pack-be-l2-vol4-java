package com.loopers.infrastructure.product;

import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.QProductStockModel;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
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
        QProductStockModel stock = QProductStockModel.productStockModel;
        QProductModel product = QProductModel.productModel;
        return Optional.ofNullable(
                queryFactory.selectFrom(stock)
                        .join(stock.product, product).fetchJoin()
                        .where(stock.id.eq(id))
                        .fetchOne()
        );
    }

    @Override
    public List<ProductStockModel> findAllByProductId(Long productId) {
        QProductStockModel stock = QProductStockModel.productStockModel;
        return queryFactory
                .selectFrom(stock)
                .where(stock.product.id.eq(productId))
                .fetch();
    }

    @Override
    public void increaseStock(Long stockId, int quantity) {
        QProductStockModel stock = QProductStockModel.productStockModel;
        queryFactory
                .update(stock)
                .set(stock.stockQuantity.value, stock.stockQuantity.value.add(quantity))
                .where(stock.id.eq(stockId))
                .execute();
    }

    @Override
    public boolean decreaseIfSufficient(Long stockId, int quantity) {
        QProductStockModel stock = QProductStockModel.productStockModel;
        return queryFactory
                .update(stock)
                .set(stock.stockQuantity.value, stock.stockQuantity.value.subtract(quantity))
                .where(stock.id.eq(stockId).and(stock.stockQuantity.value.goe(quantity)))
                .execute() > 0;
    }

    @Override
    public boolean updateStockAttributes(Long productId, Long stockId, Long price, Integer delta) {
        QProductStockModel stock = QProductStockModel.productStockModel;
        JPAUpdateClause update = queryFactory.update(stock);
        if (price != null) update.set(stock.price.value, price);
        if (delta != null) update.set(stock.stockQuantity.value, stock.stockQuantity.value.add(delta));
        BooleanExpression where = stock.id.eq(stockId).and(stock.product.id.eq(productId));
        if (delta != null) where = where.and(stock.stockQuantity.value.add(delta).goe(0));
        return update.where(where).execute() > 0;
    }
}
