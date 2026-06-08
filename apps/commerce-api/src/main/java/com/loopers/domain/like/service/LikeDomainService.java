package com.loopers.domain.like.service;

import com.loopers.domain.like.model.Like;
import com.loopers.domain.like.repository.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class LikeDomainService {

    private final LikeRepository likeRepository;

    public boolean addLike(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        likeRepository.save(Like.create(userId, productId));
        return true;
    }

    public boolean removeLike(Long userId, Long productId) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId);
        return true;
    }

    public List<Like> getLikes(Long requestUserId, Long targetUserId) {
        if (!requestUserId.equals(targetUserId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 유저의 좋아요 목록은 조회할 수 없습니다.");
        }
        return likeRepository.findAllByUserId(targetUserId);
    }
}
