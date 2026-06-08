package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.QProductStockModel;
import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.enums.ProductStatus;
import com.loopers.domain.wishlist.QWishlistModel;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        where.and(product.status.eq(ProductStatus.ACTIVE));
        where.and(product.deletedAt.isNull());
        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }

        List<ProductModel> content = switch (sort) {
            case LATEST -> findAllOrderByLatest(product, where, pageable);
            case PRICE_ASC -> findAllOrderByPriceAsc(product, where, pageable);
            case LIKES_DESC -> findAllOrderByLikesDesc(product, where, pageable);
        };

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

    private List<ProductModel> findAllOrderByLatest(QProductModel product, BooleanBuilder where, Pageable pageable) {
        return queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(product.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private List<ProductModel> findAllOrderByPriceAsc(QProductModel product, BooleanBuilder where, Pageable pageable) {
        QProductStockModel stock = QProductStockModel.productStockModel;

        List<Long> sortedIds = queryFactory
                .select(product.id)
                .from(product)
                .leftJoin(stock).on(stock.product.id.eq(product.id))
                .where(where)
                .groupBy(product.id)
                .orderBy(stock.price.value.min().asc().nullsLast())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return fetchByOrderedIds(product, sortedIds);
    }

    private List<ProductModel> findAllOrderByLikesDesc(QProductModel product, BooleanBuilder where, Pageable pageable) {
        QWishlistModel wishlist = QWishlistModel.wishlistModel;

        List<Long> sortedIds = queryFactory
                .select(product.id)
                .from(product)
                .leftJoin(wishlist).on(wishlist.productId.eq(product.id))
                .where(where)
                .groupBy(product.id)
                .orderBy(wishlist.id.count().desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return fetchByOrderedIds(product, sortedIds);
    }

    private List<ProductModel> fetchByOrderedIds(QProductModel product, List<Long> sortedIds) {
        if (sortedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductModel> productMap = queryFactory
                .selectFrom(product)
                .where(product.id.in(sortedIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        return sortedIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}