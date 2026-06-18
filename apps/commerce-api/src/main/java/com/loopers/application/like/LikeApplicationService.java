package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        likeRepository.save(new LikeModel(userId, productId));
        productRepository.incrementLikeCount(productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 내역이 없습니다."));
        likeRepository.delete(like.getId());
        productRepository.decrementLikeCount(productId);
    }

    @Transactional(readOnly = true)
    public List<LikeInfo> getLikedProducts(Long userId) {
        List<LikeModel> likes = likeRepository.findAllByUserId(userId);
        if (likes.isEmpty()) return List.of();

        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();

        Map<Long, ProductModel> productsById = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        Map<Long, Long> likeCountById = likeRepository.countByProductIds(productIds);

        return likes.stream()
            .filter(like -> productsById.containsKey(like.getProductId()))
            .map(like -> LikeInfo.from(
                like,
                productsById.get(like.getProductId()),
                likeCountById.getOrDefault(like.getProductId(), 0L)
            ))
            .toList();
    }
}
