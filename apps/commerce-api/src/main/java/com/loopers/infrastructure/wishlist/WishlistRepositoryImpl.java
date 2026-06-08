package com.loopers.infrastructure.wishlist;

import com.loopers.domain.brand.QBrandModel;
import com.loopers.domain.product.QProductModel;
import com.loopers.domain.product.QProductStockModel;
import com.loopers.domain.wishlist.QWishlistModel;
import com.loopers.domain.wishlist.WishlistModel;
import com.loopers.domain.wishlist.WishlistProductSnapshot;
import com.loopers.domain.wishlist.WishlistRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class WishlistRepositoryImpl implements WishlistRepository {

    private final WishlistJpaRepository wishlistJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public WishlistModel save(WishlistModel wishlist) {
        return wishlistJpaRepository.save(wishlist);
    }

    @Override
    public Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId) {
        return wishlistJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<WishlistModel> findAllByUserId(Long userId) {
        return wishlistJpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<WishlistProductSnapshot> findLikedProductSnapshotsByUserId(Long userId) {
        QWishlistModel wishlist = QWishlistModel.wishlistModel;
        QProductModel product = QProductModel.productModel;
        QBrandModel brand = QBrandModel.brandModel;
        QProductStockModel stock = QProductStockModel.productStockModel;

        return queryFactory
                .select(Projections.constructor(WishlistProductSnapshot.class,
                        product.id,
                        product.name.value,
                        product.status,
                        brand.name,
                        stock.price.value,
                        stock.stockQuantity.value
                ))
                .from(wishlist)
                .join(product).on(product.id.eq(wishlist.productId))
                .join(brand).on(brand.id.eq(product.brandId))
                .join(stock).on(stock.product.id.eq(product.id))
                .where(wishlist.userId.eq(userId))
                .fetch();
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        QWishlistModel wishlist = QWishlistModel.wishlistModel;
        long deleted = queryFactory
                .delete(wishlist)
                .where(wishlist.userId.eq(userId).and(wishlist.productId.eq(productId)))
                .execute();
        return (int) deleted;
    }

    @Override
    public long countByProductId(Long productId) {
        return wishlistJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countsByProductIds(List<Long> productIds) {
        QWishlistModel wishlist = QWishlistModel.wishlistModel;
        return queryFactory
                .select(wishlist.productId, wishlist.id.count())
                .from(wishlist)
                .where(wishlist.productId.in(productIds))
                .groupBy(wishlist.productId)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(wishlist.productId),
                        tuple -> tuple.get(wishlist.id.count())
                ));
    }
}
