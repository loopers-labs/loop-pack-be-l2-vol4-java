package com.loopers.tddstudy.application.like;

import com.loopers.tddstudy.domain.like.Like;
import com.loopers.tddstudy.domain.like.LikeRepository;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    public LikeService(LikeRepository likeRepository, ProductRepository productRepository) {
        this.likeRepository = likeRepository;
        this.productRepository = productRepository;
    }

    public void addLike(Long userId, Long productId) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        // 멱등: 이미 좋아요한 경우 아무 변경 없음
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }

        likeRepository.save(new Like(userId, productId));
        product.increaseLikeCount();
        productRepository.save(product);
    }

    public void cancelLike(Long userId, Long productId) {
        // 멱등: 좋아요하지 않은 경우 아무 변경 없음
        likeRepository.findByUserIdAndProductId(userId, productId).ifPresent(like -> {
            likeRepository.delete(like);
            productRepository.findById(productId).ifPresent(product -> {
                product.decreaseLikeCount();
                productRepository.save(product);
            });
        });
    }

    @Transactional(readOnly = true)
    public List<Long> getMyLikes(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
                .map(Like::getProductId)
                .collect(Collectors.toList());
    }
}
