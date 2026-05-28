package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final StockService stockService;

    /** 좋아요 등록 — 멱등: 이미 좋아요 시 likeCount 변경 없이 반환 */
    @Transactional
    public LikeInfo like(UUID productId, UserModel user) {
        ProductModel product = productService.getActive(productId);
        boolean isNew = likeService.find(user.getId(), productId).isEmpty();
        if (isNew) {
            likeService.like(user.getId(), productId);
            product.incrementLikeCount();
        }
        return LikeInfo.of(productId, product.getLikeCount());
    }

    /** 좋아요 취소 — 멱등: 없는 좋아요 취소 시 likeCount 변경 없이 반환 */
    @Transactional
    public LikeInfo unlike(UUID productId, UserModel user) {
        ProductModel product = productService.getActive(productId);
        boolean deleted = likeService.unlike(user.getId(), productId);
        if (deleted) {
            product.decrementLikeCount();
        }
        return LikeInfo.of(productId, product.getLikeCount());
    }

    /** 좋아요 목록 조회 — 본인 것만 허용, 타인 접근 시 404 */
    public Page<ProductInfo> getLikeList(UUID userId, UserModel authenticatedUser, Pageable pageable) {
        if (!userId.equals(authenticatedUser.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "접근할 수 없습니다.");
        }
        return likeService.findAllByUserId(userId, pageable).map(like -> {
            ProductModel product = productService.getActive(like.getProductId());
            StockModel stock = stockService.getByProductId(like.getProductId());
            return ProductInfo.from(product, stock);
        });
    }
}
