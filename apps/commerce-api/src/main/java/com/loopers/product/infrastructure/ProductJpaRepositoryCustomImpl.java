package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.product.domain.ProductStatus;
import com.loopers.product.domain.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ProductJpaRepositoryCustomImpl implements ProductJpaRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Product> findAllOnSale(Long brandId, ProductSortOption sort, long offset, int limit) {
        QProduct product = QProduct.product;

        JPAQuery<Product> query = queryFactory
                .selectFrom(product)
                .where(onSale(product, brandId));

        return switch (sort) {
            case LATEST -> query
                    .orderBy(product.createdAt.desc(), product.id.desc())
                    .offset(offset).limit(limit).fetch();
            case PRICE_ASC -> query
                    .orderBy(product.price.value.asc(), product.id.desc())
                    .offset(offset).limit(limit).fetch();
            case LIKES_DESC -> query
                    .orderBy(product.likeCount.desc(), product.id.desc())
                    .offset(offset).limit(limit).fetch();
        };
    }

    @Override
    public long countOnSale(Long brandId) {
        QProduct product = QProduct.product;
        Long count = queryFactory
                .select(product.count())
                .from(product)
                .where(onSale(product, brandId))
                .fetchOne();
        return count == null ? 0L : count;
    }

    private BooleanBuilder onSale(QProduct product, Long brandId) {
        BooleanBuilder where = new BooleanBuilder()
                .and(product.status.eq(ProductStatus.ON_SALE))
                .and(product.deletedAt.isNull());
        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }
        return where;
    }
}
