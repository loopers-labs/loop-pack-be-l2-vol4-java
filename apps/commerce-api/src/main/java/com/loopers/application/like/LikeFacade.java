package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final StockService stockService;

    public void like(Long userId, Long productId) {
        likeService.like(userId, productId);
    }

    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
    }

    /**
     * 내가 좋아요한 상품 목록.
     *
     * <p>재고/좋아요 수는 IN 쿼리로 일괄 조회하여 N+1을 회피한다.
     * 다중 원천 데이터를 DTO 리스트로 묶는 책임은 {@link ProductInfo#assembleUserList} 에 위임한다.
     */
    public List<ProductInfo> getLikedProducts(Long userId) {
        List<ProductModel> products = likeService.getLikedProducts(userId);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return ProductInfo.assembleUserList(
            products,
            stockService.getStocksByProductIds(productIds),
            likeService.countByProductIdIn(productIds)
        );
    }
}
