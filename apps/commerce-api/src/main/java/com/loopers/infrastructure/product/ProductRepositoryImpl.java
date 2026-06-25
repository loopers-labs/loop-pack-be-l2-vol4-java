package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.product.QProductModel;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.loopers.domain.product.QProductModel.productModel;

@Component
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ProductModel save(final ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(final Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public void increaseLikeCount(final Long productId) {
        productJpaRepository.increaseLikeCount(productId);
    }

    @Override
    public void decreaseLikeCount(final Long productId) {
        productJpaRepository.decreaseLikeCount(productId);
    }

    @Override
    public Page<ProductModel> search(final @Nullable Long brandId, final @Nullable ProductStatus status, final @Nullable ProductSortType sort, final Pageable pageable) {
        final List<ProductModel> content = queryFactory
                .selectFrom(productModel)
                .where(
                        isNotDeleted(),
                        isBrandIdProvided(brandId),
                        isStatusProvided(status)
                )
                .orderBy(toOrder(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        final Long total = queryFactory
                .select(productModel.count())
                .from(productModel)
                .where(
                        isNotDeleted(), 
                        isBrandIdProvided(brandId), 
                        isStatusProvided(status)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private BooleanExpression isNotDeleted() {
        return productModel.deletedAt.isNull();
    }

    private BooleanExpression isBrandIdProvided(final @Nullable Long brandId) {
        return brandId == null ? null : productModel.brandId.eq(brandId);
    }

    private BooleanExpression isStatusProvided(final @Nullable ProductStatus status) {
        return status == null ? null : productModel.status.eq(status);
    }

    private OrderSpecifier<?> toOrder(final @Nullable ProductSortType sort) {
        if (sort == null) {
            return productModel.createdAt.desc();
        }
        return switch (sort) {
            case PRICE_ASC -> productModel.price.value.asc();
            case PRICE_DESC -> productModel.price.value.desc();
            case LIKES_DESC -> productModel.likeCount.desc();
        };
    }
}
