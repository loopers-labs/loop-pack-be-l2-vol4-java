package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        LikeId id = LikeId.of(userId, productId);
        Optional<LikeModel> likeModel = likeRepository.find(id);
        if (likeModel.isEmpty()) {
            likeRepository.save(LikeModel.of(userId, productId));
            return;
        }
        likeModel.get().like();
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeId id = LikeId.of(userId, productId);
        likeRepository.find(id).ifPresent(LikeModel::unlike);
    }
}