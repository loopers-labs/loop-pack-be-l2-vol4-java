package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.enums.ProductStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public boolean existsByBrandIdAndName(Long brandId, String name) {
        QProductModel product = QProductModel.productModel;
        return queryFactory
                .selectOne()
                .from(product)
                .where(product.brandId.eq(brandId).and(product.name.value.eq(name)))
                .fetchFirst() != null;
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public void suspendAllByBrandId(Long brandId) {
        QProductModel product = QProductModel.productModel;
        queryFactory
                .update(product)
                .set(product.status, ProductStatus.INACTIVE)
                .where(product.brandId.eq(brandId))
                .execute();
    }

    @Override
    public Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable) {
        QProductModel product = QProductModel.productModel;

        BooleanBuilder where = new BooleanBuilder();
        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }
        where.and(product.status.eq(ProductStatus.ACTIVE));
        where.and(product.deletedAt.isNull());

        List<OrderSpecifier<?>> orderSpecifiers = switch (sort) {
            case LATEST -> List.of(product.createdAt.desc(), product.id.desc());
            case PRICE_ASC -> List.of(product.minPrice.asc(), product.id.asc());
            case LIKES_DESC -> List.of(product.likeCount.desc(), product.id.desc());
        };

        List<ProductModel> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(orderSpecifiers.toArray(OrderSpecifier[]::new))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productJpaRepository.findAllById(ids);
    }

    @Override
    public void increaseLikeCount(Long productId) {
        QProductModel product = QProductModel.productModel;
        queryFactory
                .update(product)
                .set(product.likeCount, product.likeCount.add(1))
                .where(product.id.eq(productId))
                .execute();
    }

    @Override
    public void decreaseLikeCount(Long productId) {
        QProductModel product = QProductModel.productModel;
        queryFactory
                .update(product)
                .set(product.likeCount, product.likeCount.add(-1))
                .where(product.id.eq(productId).and(product.likeCount.gt(0)))
                .execute();
    }

    @Override
    public Page<ProductModel> findAllForAdmin(Long brandId, Pageable pageable) {
        QProductModel product = QProductModel.productModel;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.deletedAt.isNull());
        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }

        List<ProductModel> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(product.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
