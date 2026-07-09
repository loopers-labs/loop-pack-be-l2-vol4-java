package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeSort;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductLikeViewRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.event.like.LikedEvent;
import com.loopers.interfaces.event.like.UnlikedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ProductLikeViewRepository productLikeViewRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(Long memberId, Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 상품을 찾을 수 없습니다."));

        boolean alreadyLiked = likeRepository.findByMemberIdAndProductId(memberId, productId).isPresent();
        if (alreadyLiked) {
            return;
        }

        try {
            likeRepository.save(new LikeModel(memberId, productId));
        } catch (DataIntegrityViolationException e) {
            return;
        }
        eventPublisher.publishEvent(new LikedEvent(memberId, productId));
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        LikeModel like = likeRepository.findByMemberIdAndProductId(memberId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 정보를 찾을 수 없습니다."));

        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.delete(like);
        eventPublisher.publishEvent(new UnlikedEvent(memberId, productId));
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        ProductLikeViewModel view = productLikeViewRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 좋아요 수 정보를 찾을 수 없습니다."));
        view.increment();
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        ProductLikeViewModel view = productLikeViewRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 좋아요 수 정보를 찾을 수 없습니다."));
        view.decrement();
    }

    @Transactional
    public void deleteAllByProductId(Long productId) {
        likeRepository.deleteAllByProductId(productId);
        productLikeViewRepository.deleteByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<LikeModel> getLikesByMemberId(Long memberId) {
        return likeRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<LikeInfo> getLikeInfosByMemberId(Long memberId, LikeSort sort) {
        List<LikeModel> likes = likeRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        if (likes.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();
        Map<Long, ProductModel> productMap = productRepository.findAllByIds(productIds)
            .stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        List<LikeInfo> infos = likes.stream()
            .filter(like -> productMap.containsKey(like.getProductId()))
            .map(like -> LikeInfo.of(like, productMap.get(like.getProductId())))
            .collect(Collectors.toList());

        if (sort == LikeSort.LIKES_DESC) {
            Map<Long, Integer> likeCountMap = productLikeViewRepository.findAllByProductIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(ProductLikeViewModel::getProductId, ProductLikeViewModel::getLikeCount));
            infos.sort(Comparator.comparingInt((LikeInfo info) -> likeCountMap.getOrDefault(info.productId(), 0)).reversed());
        }

        return infos;
    }
}
