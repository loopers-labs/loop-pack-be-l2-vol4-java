package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductInfoAssembler;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final ProductInfoAssembler productInfoAssembler;
    private final UserService userService;

    @Transactional
    public void like(String loginId, String loginPw, Long productId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        ProductModel product = productService.getById(productId);
        likeService.register(user.getId(), product.getId());
    }

    @Transactional
    public void unlike(String loginId, String loginPw, Long productId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        ProductModel product = productService.getById(productId);
        likeService.cancel(user.getId(), product.getId());
    }

    public List<ProductInfo> getLikedProducts(String loginId, String loginPw, Long userId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);
        user.validateOwner(userId);
        List<Long> productIds = likeService.getLikedProductIds(userId);
        List<ProductModel> products = productService.findAllByIds(productIds);
        return productInfoAssembler.toInfoList(products);
    }
}
