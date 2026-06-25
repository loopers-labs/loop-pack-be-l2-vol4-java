package com.loopers.product.infrastructure;

import com.loopers.brand.application.BrandInfo;
import com.loopers.brand.domain.QBrand;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductDetailQuery;
import com.loopers.product.domain.QProduct;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductDetailQueryDsl implements ProductDetailQuery {

    private static final QProduct product = QProduct.product;
    private static final QBrand brand = QBrand.brand;
    private static final QProductLikeSummary summary = QProductLikeSummary.productLikeSummary;

    private final JPAQueryFactory queryFactory;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDetailInfo> findVisibleProduct(Long productId) {
        ProductDetailInfo info = queryFactory
            .select(Projections.constructor(
                ProductDetailInfo.class,
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
                summary.likeCount.coalesce(0L)
            ))
            .from(product)
            .join(brand).on(brand.id.eq(product.brandId))
            .leftJoin(summary).on(summary.productId.eq(product.id))
            .where(
                product.id.eq(productId),
                product.deletedAt.isNull(),
                brand.deletedAt.isNull()
            )
            .fetchOne();

        return Optional.ofNullable(info);
    }
}
