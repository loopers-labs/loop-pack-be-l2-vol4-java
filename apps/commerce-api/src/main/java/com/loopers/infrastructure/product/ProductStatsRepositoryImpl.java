package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.product.QProductStatsModel;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductStatsRepositoryImpl implements ProductStatsRepository {

    private final ProductStatsJpaRepository productStatsJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public ProductStatsModel save(ProductStatsModel productStats) {
        return productStatsJpaRepository.save(productStats);
    }

    @Override
    public Optional<ProductStatsModel> findByProduct(ProductModel product) {
        return productStatsJpaRepository.findByProduct(product);
    }

    @Override
    public List<ProductStatsModel> findAllByProductIds(List<Long> productIds) {
        return productStatsJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public void increaseLikeCount(Long productId) {
        productStatsJpaRepository.increaseLikeCount(productId);
    }

    @Override
    public void decreaseLikeCount(Long productId) {
        productStatsJpaRepository.decreaseLikeCount(productId);
    }

    @Override
    public Page<ProductStatsModel> findPageOrderByLikeCountDesc(Pageable pageable) {
        QProductStatsModel stats = QProductStatsModel.productStatsModel;

        List<ProductStatsModel> content = queryFactory
            .selectFrom(stats)
            .orderBy(stats.likeCount.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(stats.count())
            .from(stats)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<ProductStatsModel> findPageByProductIdsOrderByLikeCountDesc(List<Long> productIds, Pageable pageable) {
        QProductStatsModel stats = QProductStatsModel.productStatsModel;

        List<ProductStatsModel> content = queryFactory
            .selectFrom(stats)
            .where(stats.product.id.in(productIds))
            .orderBy(stats.likeCount.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        Long total = queryFactory
            .select(stats.count())
            .from(stats)
            .where(stats.product.id.in(productIds))
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}
