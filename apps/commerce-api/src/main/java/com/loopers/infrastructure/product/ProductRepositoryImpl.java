package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.QProductModel;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
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
    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandId(brandId, pageable);
    }

    @Override
    public Page<ProductModel> findProducts(Long brandId, Pageable pageable) {
        QProductModel product = QProductModel.productModel;

        BooleanBuilder condition = new BooleanBuilder();
        if (brandId != null) {
            condition.and(product.brandId.eq(brandId));
        }

        OrderSpecifier<?>[] orderSpecifiers = toOrderSpecifiers(pageable.getSort(), product);

        List<ProductModel> content = queryFactory
            .selectFrom(product)
            .where(condition)
            .orderBy(orderSpecifiers)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(product.count())
            .from(product)
            .where(condition)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public void softDeleteAllByBrandId(Long brandId, ZonedDateTime deletedAt) {
        productJpaRepository.softDeleteAllByBrandId(brandId, deletedAt);
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort, QProductModel product) {
        return sort.stream()
            .map(order -> (OrderSpecifier<?>) switch (order.getProperty()) {
                case "id" -> order.isDescending() ? product.id.desc() : product.id.asc();
                case "price" -> order.isDescending() ? product.price.desc() : product.price.asc();
                default -> product.id.desc();
            })
            .toArray(OrderSpecifier[]::new);
    }
}
