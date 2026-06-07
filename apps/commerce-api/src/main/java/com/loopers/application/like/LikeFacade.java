package com.loopers.application.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class LikeFacade {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;

    public void createLike(Long userId, Long productId) {
        UserModel user = userRepository.getActiveById(userId);
        ProductModel product = productRepository.getActiveById(productId);

        if (likeRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            return;
        }

        LikeModel like = LikeModel.builder()
            .userId(user.getId())
            .productId(product.getId())
            .build();

        likeRepository.save(like);
    }

    public void deleteLike(Long userId, Long productId) {
        UserModel user = userRepository.getActiveById(userId);
        ProductModel product = productRepository.getActiveById(productId);

        likeRepository.deleteByUserIdAndProductId(user.getId(), product.getId());
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryInfo> readLikedProducts(Long authUserId, Long pathUserId, int page, int size) {
        if (!authUserId.equals(pathUserId)) {
            return Page.empty(PageRequest.of(page, size));
        }

        return likeRepository.findLikedProductSummaries(authUserId, page, size)
            .map(ProductSummaryInfo::from);
    }
}
