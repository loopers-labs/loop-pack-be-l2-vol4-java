package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductPage;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductSortDirection;
import com.loopers.domain.product.ProductSortType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.dsl.PathBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private static final PathBuilder<ProductModel> product = new PathBuilder<>(ProductModel.class, "productModel");

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public int deductStock(Long id, int quantity) {
        return productJpaRepository.deductStock(id, quantity);
    }

    @Override
    public int increaseLikeCount(Long id) {
        return productJpaRepository.increaseLikeCount(id);
    }

    @Override
    public int decreaseLikeCount(Long id) {
        return productJpaRepository.decreaseLikeCount(id);
    }

    @Override
    public List<ProductModel> findAll() {
        return queryFactory
            .selectFrom(product)
            .where(activeProducts(null))
            .fetch();
    }

    @Override
    public ProductPage search(ProductSearchCondition condition) {
        BooleanBuilder where = activeProducts(condition.brandId());
        JPAQuery<ProductModel> query = queryFactory
            .selectFrom(product)
            .where(where)
            .offset((long) condition.page() * condition.size())
            .limit(condition.size());
        List<ProductModel> products = applyOrder(query, condition.sortType(), condition.sortDirection()).fetch();
        Long totalElements = queryFactory
            .select(product.count())
            .from(product)
            .where(where)
            .fetchOne();
        return new ProductPage(
            products,
            condition.page(),
            condition.size(),
            totalElements == null ? 0L : totalElements,
            calculateTotalPages(totalElements == null ? 0L : totalElements, condition.size())
        );
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.findByIdAndDeletedAtIsNull(id).ifPresent(product -> {
            product.delete();
            productJpaRepository.save(product);
        });
    }

    private BooleanBuilder activeProducts(Long brandId) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(product.getDateTime("deletedAt", ZonedDateTime.class).isNull());
        if (brandId != null) {
            builder.and(product.getNumber("brandId", Long.class).eq(brandId));
        }
        return builder;
    }

    private JPAQuery<ProductModel> applyOrder(
        JPAQuery<ProductModel> query,
        ProductSortType sortType,
        ProductSortDirection direction
    ) {
        return switch (sortType) {
            case LATEST -> query.orderBy(
                product.getDateTime("createdAt", ZonedDateTime.class).desc(),
                product.getNumber("id", Long.class).desc()
            );
            case PRICE -> query.orderBy(
                direction == ProductSortDirection.ASC
                    ? product.getNumber("price", Long.class).asc()
                    : product.getNumber("price", Long.class).desc(),
                product.getNumber("id", Long.class).desc()
            );
            case LIKE_COUNT -> query.orderBy(
                direction == ProductSortDirection.ASC
                    ? product.getNumber("likeCount", Long.class).asc()
                    : product.getNumber("likeCount", Long.class).desc(),
                product.getNumber("id", Long.class).desc()
            );
        };
    }

    private int calculateTotalPages(long totalElements, int size) {
        return (int) Math.ceil((double) totalElements / size);
    }
}
