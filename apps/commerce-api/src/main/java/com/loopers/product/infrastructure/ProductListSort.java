package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductSort;
import com.loopers.product.domain.QProduct;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;

final class ProductListSort {

    private ProductListSort() {
    }

    static OrderSpecifier<?>[] from(ProductSort sort, QProduct product, Expression<Long> likeCount) {
        return switch (sort) {
            case PRICE_ASC -> withLatest(product.price.value.asc(), product);
            case LIKES_DESC -> withLatest(new OrderSpecifier<>(Order.DESC, likeCount), product);
            case LATEST -> latest(product);
        };
    }

    private static OrderSpecifier<?>[] withLatest(OrderSpecifier<?> primaryOrder, QProduct product) {
        return new OrderSpecifier<?>[] {
            primaryOrder,
            product.createdAt.desc(),
            product.id.desc()
        };
    }

    private static OrderSpecifier<?>[] latest(QProduct product) {
        return new OrderSpecifier<?>[] {
            product.createdAt.desc(),
            product.id.desc()
        };
    }
}
