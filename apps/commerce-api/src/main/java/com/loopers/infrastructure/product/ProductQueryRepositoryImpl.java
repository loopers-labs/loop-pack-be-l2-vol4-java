package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductQueryRepository;
import com.loopers.infrastructure.brand.QBrandJpaEntity;
import com.loopers.infrastructure.inventory.QInventoryJpaEntity;
import com.loopers.infrastructure.metrics.QProductMetricSummaryJpaEntity;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductQueryRepositoryImpl implements ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QProductJpaEntity product = QProductJpaEntity.productJpaEntity;
    private static final QBrandJpaEntity brand = QBrandJpaEntity.brandJpaEntity;
    private static final QInventoryJpaEntity inventory = QInventoryJpaEntity.inventoryJpaEntity;
    private static final QProductMetricSummaryJpaEntity metrics = QProductMetricSummaryJpaEntity.productMetricSummaryJpaEntity;

    @Override
    public Page<ProductInfo> findAllWithDetails(String brandId, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder()
                .and(product.deletedAt.isNull());

        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }

        List<ProductInfo> content = queryFactory
                .select(Projections.constructor(ProductInfo.class,
                        product.id,
                        product.brandId,
                        brand.name,
                        product.name,
                        product.description,
                        product.price,
                        Expressions.cases()
                                .when(metrics.likeCount.isNotNull()).then(metrics.likeCount)
                                .otherwise(0L),
                        inventory.quantity,
                        product.createdAt,
                        product.updatedAt))
                .from(product)
                .join(brand).on(brand.id.eq(product.brandId))
                .join(inventory).on(inventory.productId.eq(product.id))
                .leftJoin(metrics).on(metrics.productId.eq(product.id))
                .where(where)
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        return sort.stream()
                .map(order -> switch (order.getProperty()) {
                    case "price" -> order.isAscending() ? product.price.asc() : product.price.desc();
                    case "likeCount" -> order.isAscending()
                            ? Expressions.cases().when(metrics.likeCount.isNotNull()).then(metrics.likeCount).otherwise(0L).asc()
                            : Expressions.cases().when(metrics.likeCount.isNotNull()).then(metrics.likeCount).otherwise(0L).desc();
                    default -> order.isAscending() ? product.id.asc() : product.id.desc();
                })
                .toArray(OrderSpecifier[]::new);
    }
}
