package com.loopers.product.infrastructure;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.brand.domain.QBrand;
import com.loopers.like.domain.QLike;
import com.loopers.product.domain.ProductSort;
import com.loopers.product.domain.QProduct;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
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
