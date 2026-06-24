package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태(이름/설명/이미지/가격/좋아요 수)만 복사 → dirty checking으로 UPDATE.
     *   soft delete 상태(deletedAt)도 도메인 기준으로 delete()/restore() 동기화한다(둘 다 멱등).
     *   (BaseEntity의 id가 final이라 도메인을 그대로 새 엔티티로 만들면 INSERT로 오인되므로 이 경로가 필요하다.)
     */
    @Override
    public ProductModel save(ProductModel product) {
        if (product.getId() == null) {
            ProductEntity saved = productJpaRepository.save(ProductEntityMapper.toEntity(product));
            return ProductEntityMapper.toDomain(saved);
        }
        ProductEntity entity = productJpaRepository.findById(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getId() + "] 상품을 찾을 수 없습니다."));
        entity.applyState(product.getName(), product.getDescription(), product.getImageUrl(),
                product.getPrice(), product.getLikesCount());
        if (product.isActive()) {
            entity.restore();
        } else {
            entity.delete();
        }
        return ProductEntityMapper.toDomain(productJpaRepository.save(entity));
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findById(id).map(ProductEntityMapper::toDomain);
    }

    @Override
    public List<ProductModel> findActiveByBrandId(Long brandId) {
        return productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId).stream()
                .map(ProductEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<ProductModel> findActiveByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return productJpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(ProductEntityMapper::toDomain)
                .toList();
    }

    @Override
    public void incrementLikesCount(Long id) {
        productJpaRepository.incrementLikesCount(id);
    }

    @Override
    public void decrementLikesCount(Long id) {
        productJpaRepository.decrementLikesCount(id);
    }

    /**
     * 키셋(커서) 페이지네이션 — QueryDSL 동적 쿼리. {@code [등치(deleted_at, brand_id)] → [정렬(커서 비교식)] →
     * [tie-break(id)]} 순서로, week5에서 만든 DESC 복합 인덱스(idx_*_likes_desc)의 물리 순서를 그대로 타게 해
     * 풀스캔·filesort를 피한다. hasNext 판별용으로 size+1 건을 읽어 상위에서 잘라낸다.
     */
    @Override
    public List<ProductModel> findActivePage(Long brandId, ProductSortType sort, ProductCursor cursor, int size) {
        QProductEntity product = QProductEntity.productEntity;

        BooleanBuilder where = new BooleanBuilder();
        where.and(product.deletedAt.isNull());
        if (brandId != null) {
            where.and(product.brandId.eq(brandId));
        }
        where.and(cursorPredicate(product, sort, cursor));

        return queryFactory.selectFrom(product)
                .where(where)
                .orderBy(orderSpecifiers(product, sort))
                .limit(size + 1L)
                .fetch()
                .stream()
                .map(ProductEntityMapper::toDomain)
                .toList();
    }

    /**
     * 키셋 비교식 — 마지막으로 읽은 행(커서) "다음"부터 읽도록 한다. cursor가 null이면 첫 페이지(조건 없음).
     * 정렬 컬럼이 같은 동점 구간은 id DESC로 잘라(tie-break) 페이지 경계에서 누락·중복을 막는다.
     */
    private static BooleanExpression cursorPredicate(QProductEntity p, ProductSortType sort, ProductCursor cursor) {
        if (cursor == null) {
            return null; // BooleanBuilder.and(null)은 무시되어 첫 페이지가 된다.
        }
        Long v = cursor.sortValue();
        Long lastId = cursor.id();
        return switch (sort) {
            case LATEST -> p.id.lt(lastId);
            case LIKES_DESC -> p.likesCount.lt(v).or(p.likesCount.eq(v).and(p.id.lt(lastId)));
            case PRICE_DESC -> p.price.lt(v).or(p.price.eq(v).and(p.id.lt(lastId)));
            case PRICE_ASC -> p.price.gt(v).or(p.price.eq(v).and(p.id.lt(lastId)));
        };
    }

    /** 정렬 + id DESC tie-break (페이지 경계 안정성 — 01 §7.2). */
    private static OrderSpecifier<?>[] orderSpecifiers(QProductEntity p, ProductSortType sort) {
        return switch (sort) {
            case LATEST -> new OrderSpecifier<?>[]{p.id.desc()};
            case LIKES_DESC -> new OrderSpecifier<?>[]{p.likesCount.desc(), p.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{p.price.desc(), p.id.desc()};
            case PRICE_ASC -> new OrderSpecifier<?>[]{p.price.asc(), p.id.desc()};
        };
    }
}
