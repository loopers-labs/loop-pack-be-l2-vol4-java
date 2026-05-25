package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.SortOption;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductQueryDslRepository {

    private final JPAQueryFactory queryFactory;

    public Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable) {
        QProductModel p = QProductModel.productModel;

        BooleanBuilder where = new BooleanBuilder().and(p.deletedAt.isNull());
        if (brandId != null) {
            where.and(p.brand.id.eq(brandId));
        }

        List<ProductModel> content = queryFactory.selectFrom(p)
            .leftJoin(p.brand).fetchJoin()
            .where(where)
            .orderBy(orderSpecifier(sort, p), p.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory.select(p.count()).from(p).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private OrderSpecifier<?> orderSpecifier(SortOption sort, QProductModel p) {
        return switch (sort) {
            case LATEST -> p.createdAt.desc();
            case PRICE_ASC -> p.price.asc();
            case LIKES_DESC -> p.likeCount.desc();
        };
    }
}
