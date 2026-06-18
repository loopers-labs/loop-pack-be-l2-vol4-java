package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.loopers.domain.product.QProductModel.productModel;

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
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public List<ProductModel> findByIds(List<Long> ids) {
        return productJpaRepository.findAllById(ids);
    }

    @Override
    public List<ProductModel> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public Page<ProductModel> findAll(Long brandId, String sort, Pageable pageable) {
        List<ProductModel> content = queryFactory
                .selectFrom(productModel)
                .where(
                        brandIdEq(brandId),
                        productModel.isDeleted.isFalse()
                )
                .orderBy(getOrderSpecifiers(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(productModel.count())
                .from(productModel)
                .where(
                        brandIdEq(brandId),
                        productModel.isDeleted.isFalse()
                )
                .fetchOne();

        long totalCount = total != null ? total : 0L;

        return new PageImpl<>(content, pageable, totalCount);
    }

    private BooleanExpression brandIdEq(Long brandId) {
        return brandId != null ? productModel.brandId.eq(brandId) : null;
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(String sort) {
        List<OrderSpecifier<?>> specifiers = new ArrayList<>();

        if ("likes_desc".equalsIgnoreCase(sort)) {
            specifiers.add(productModel.likeCount.desc());
        }

        specifiers.add(productModel.createdAt.desc());

        return specifiers.toArray(new OrderSpecifier[0]);
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public void deleteByBrandId(Long brandId) {
    }

    @Override
    public Optional<ProductModel> findByIdWithLock(Long id) {
        return productJpaRepository.findByIdWithLock(id);
    }
}
