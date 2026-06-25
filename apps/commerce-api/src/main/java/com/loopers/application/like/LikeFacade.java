package com.loopers.application.like;

import com.loopers.domain.like.LikeSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;

    public void like(Long memberId, Long productId) {
        likeService.like(memberId, productId);
    }

    public void unlike(Long memberId, Long productId) {
        likeService.unlike(memberId, productId);
    }

    public List<LikeInfo> getLikes(Long memberId, LikeSort sort) {
        return likeService.getLikeInfosByMemberId(memberId, sort);
    }
}
