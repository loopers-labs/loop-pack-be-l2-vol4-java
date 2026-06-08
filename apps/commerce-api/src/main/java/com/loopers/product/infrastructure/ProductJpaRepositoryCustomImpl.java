package com.loopers.product.infrastructure;

import com.loopers.like.domain.QLike;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.product.domain.ProductStatus;
import com.loopers.product.domain.QProduct;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ProductJpaRepositoryCustomImpl implements ProductJpaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Product> findAllOnSale(ProductSortOption sort) {
        QProduct product = QProduct.product;
        QLike like = QLike.like;

        JPAQuery<Product> query = queryFactory
                .selectFrom(product)
                .where(product.status.eq(ProductStatus.ON_SALE), product.deletedAt.isNull());

        return switch (sort) {
            case LATEST -> query.orderBy(product.createdAt.desc(), product.id.desc()).fetch();
            case PRICE_ASC -> query.orderBy(product.price.asc(), product.id.desc()).fetch();
            case LIKES_DESC -> query
                    .leftJoin(like).on(like.productId.eq(product.id), like.deletedAt.isNull())
                    .groupBy(product.id)
                    .orderBy(like.id.count().desc(), product.id.desc())
                    .fetch();
        };
    }
}
