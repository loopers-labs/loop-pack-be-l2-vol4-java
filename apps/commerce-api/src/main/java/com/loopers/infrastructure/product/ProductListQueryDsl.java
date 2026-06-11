package com.loopers.infrastructure.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductListInfo;
import com.loopers.application.product.ProductListQuery;
import com.loopers.domain.brand.QBrand;
import com.loopers.domain.like.QLike;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.QProduct;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductListQueryDsl implements ProductListQuery {

    private static final QProduct product = QProduct.product;
    private static final QBrand brand = QBrand.brand;
    private static final QLike like = new QLike("productListLike");

    private final JPAQueryFactory queryFactory;

    @Override
    public PageResult<ProductListInfo> findVisibleProducts(PageQuery query, Long brandId, ProductSort sort) {
        Expression<Long> likeCount = likeCount();
        List<ProductListInfo> content = queryFactory
            .select(Projections.constructor(
                ProductListInfo.class,
                product.id,
                Projections.constructor(
                    BrandInfo.class,
                    brand.id,
                    brand.name.value,
                    brand.description,
                    brand.createdAt,
                    brand.updatedAt,
                    brand.deletedAt
                ),
                product.name.value,
                product.description.value,
                product.price.value,
                likeCount
            ))
            .from(product)
            .join(brand).on(brand.id.eq(product.brandId))
            .where(
                product.deletedAt.isNull(),
                brand.deletedAt.isNull(),
                brandIdEq(brandId)
            )
            .orderBy(ProductListSort.from(sort, product, likeCount))
            .offset((long) query.page() * query.size())
            .limit(query.size())
            .fetch();

        return PageResult.from(new PageImpl<>(
            content,
            PageRequest.of(query.page(), query.size()),
            countVisibleProducts(brandId)
        ));
    }

    private long countVisibleProducts(Long brandId) {
        Long count = queryFactory
            .select(Wildcard.count)
            .from(product)
            .join(brand).on(brand.id.eq(product.brandId))
            .where(
                product.deletedAt.isNull(),
                brand.deletedAt.isNull(),
                brandIdEq(brandId)
            )
            .fetchOne();

        return count == null ? 0L : count;
    }

    private Expression<Long> likeCount() {
        return JPAExpressions
            .select(like.id.count())
            .from(like)
            .where(like.productId.eq(product.id));
    }

    private BooleanExpression brandIdEq(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return product.brandId.eq(brandId);
    }

}
