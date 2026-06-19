package com.loopers.product.infrastructure;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.brand.domain.QBrand;
import com.loopers.product.domain.ProductSort;
import com.loopers.product.domain.QProduct;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductListQueryDsl implements ProductListQuery {

    private static final QProduct product = QProduct.product;
    private static final QBrand brand = QBrand.brand;
    private static final QProductLikeSummary summary = QProductLikeSummary.productLikeSummary;

    private final JPAQueryFactory queryFactory;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductListInfo> findVisibleProducts(PageQuery query, Long brandId, ProductSort sort) {
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
                summary.likeCount
            ))
            .from(summary)
            .join(product).on(product.id.eq(summary.productId))
            .join(brand).on(brand.id.eq(product.brandId))
            .where(
                product.deletedAt.isNull(),
                brand.deletedAt.isNull(),
                summaryBrandIdEq(brandId)
            )
            .orderBy(ProductListSort.from(sort, product, summary.likeCount))
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
                productBrandIdEq(brandId)
            )
            .fetchOne();

        return count == null ? 0L : count;
    }

    private BooleanExpression summaryBrandIdEq(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return summary.brandId.eq(brandId);
    }

    private BooleanExpression productBrandIdEq(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return product.brandId.eq(brandId);
    }

}
