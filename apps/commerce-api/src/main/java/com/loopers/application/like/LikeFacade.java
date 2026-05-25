package com.loopers.application.like;

import com.loopers.application.product.ProductListInfo;
import com.loopers.application.product.ProductListInfoAssembler;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final ProductService productService;
    private final LikeService likeService;
    private final ProductListInfoAssembler productListInfoAssembler;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getProduct(productId);
        likeService.like(userId, productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductListInfo> getMyLikes(GetMyLikesCommand command) {
        if (!command.isOwnUser()) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 좋아요 목록은 조회할 수 없습니다.");
        }

        PageResult<Product> products = productService.getLikedProducts(
            command.userId(),
            new PageQuery(command.page(), command.size())
        );
        return productListInfoAssembler.assembleLikedProducts(products);
    }
}
