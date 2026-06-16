package com.loopers.like.application;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRegistrationPolicy;
import com.loopers.like.domain.LikeRepository;
import com.loopers.like.domain.LikeService;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final LikeRegistrationPolicy likeRegistrationPolicy;

    @Caching(evict = {
        @CacheEvict(cacheNames = "product", key = "#productId"),
        @CacheEvict(cacheNames = "products", allEntries = true)
    })
    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        Optional<LikeModel> existing = likeRepository.findByUserIdAndProductId(userId, productId);
        likeRegistrationPolicy.check(productRepository.find(productId), existing);

        LikeModel like = likeService.createLike(userId, productId);
        LikeInfo saved = LikeInfo.from(likeRepository.save(like));
        productRepository.incrementLikeCount(productId);
        return saved;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "product", key = "#productId"),
        @CacheEvict(cacheNames = "products", allEntries = true)
    })
    @Transactional
    public void cancelLike(Long userId, Long productId) {
        LikeModel like = likeService.cancelLike(likeRepository.findByUserIdAndProductId(userId, productId));
        likeRepository.delete(like);
        productRepository.decrementLikeCount(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(Long userId) {
        List<LikeModel> likes = likeRepository.findAllByUserId(userId);
        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();
        // [fix] N+1 문제 → productId 목록으로 IN 쿼리 일괄 조회 (결정 10)
        List<ProductModel> products = productRepository.findAllByIds(productIds);
        Map<Long, Integer> stockMap = stockRepository.findAllByProductIds(productIds).stream()
            .collect(Collectors.toMap(StockModel::getProductId, StockModel::availableStock));
        return products.stream()
            .map(p -> ProductInfo.from(p, stockMap.getOrDefault(p.getId(), 0)))
            .toList();
    }
}
