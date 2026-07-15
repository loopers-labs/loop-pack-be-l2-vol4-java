package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductPage;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.QProductModel;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductQueryDslRepository {

    private final JPAQueryFactory queryFactory;

    public ProductPage search(ProductSearchCondition condition) {
        QProductModel product = QProductModel.productModel;

        BooleanBuilder where = new BooleanBuilder(product.deletedAt.isNull());
        if (condition.brandId() != null) {
            where.and(product.brandId.eq(condition.brandId()));
        }

        // 동점(likeCount 동일) 시 페이지 경계에서 중복/누락이 없도록 id 를 tie-breaker 로 사용한다.
        OrderSpecifier<?>[] orderBy = switch (condition.sortType()) {
            case LIKES_DESC -> new OrderSpecifier<?>[] {product.likeCount.desc(), product.id.desc()};
            case LATEST -> new OrderSpecifier<?>[] {product.id.desc()};
        };

        List<ProductModel> items = queryFactory
            .selectFrom(product)
            .where(where)
            .orderBy(orderBy)
            .offset(condition.offset())
            .limit(condition.size())
            .fetch();

        Long totalCount = queryFactory
            .select(product.count())
            .from(product)
            .where(where)
            .fetchOne();

        return new ProductPage(items, condition.page(), condition.size(), totalCount == null ? 0L : totalCount);
    }
}
