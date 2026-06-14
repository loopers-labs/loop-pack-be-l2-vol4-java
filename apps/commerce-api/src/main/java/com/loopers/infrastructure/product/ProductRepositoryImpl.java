package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.loopers.domain.product.QProduct.product;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public List<Product> findAllByIdForUpdate(List<Long> ids) {
        return productJpaRepository.findAllByIdForUpdate(ids);
    }

    @Override
    public void increaseLikeCount(Long id) {
        productJpaRepository.increaseLikeCount(id);
    }

    @Override
    public void decreaseLikeCount(Long id) {
        productJpaRepository.decreaseLikeCount(id);
    }

    @Override
    public List<Product> findAll(Long brandId, ProductSort sort, int page, int size) {
        return queryFactory
            .selectFrom(product)
            .where(brandIdEq(brandId))
            .orderBy(toOrder(sort), product.id.desc())
            .offset((long) page * size)
            .limit(size)
            .fetch();
    }

    @Override
    public long count(Long brandId) {
        Long total = queryFactory
            .select(product.count())
            .from(product)
            .where(brandIdEq(brandId))
            .fetchOne();
        return total == null ? 0L : total;
    }

    private BooleanExpression brandIdEq(Long brandId) {
        return brandId == null ? null : product.brandId.eq(brandId);
    }

    private OrderSpecifier<?> toOrder(ProductSort sort) {
        return switch (sort == null ? ProductSort.LATEST : sort) {
            case PRICE_ASC -> product.price.amount.asc();
            case LIKES_DESC -> product.likeCount.desc();
            case LATEST -> product.createdAt.desc();
        };
    }
}
