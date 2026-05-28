package com.loopers.support.fake;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FakeLikeRepository implements LikeRepository {

    private final List<LikeModel> store = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public LikeModel save(LikeModel like) {
        if (like.getId() == null || like.getId() == 0L) {
            IdFixtures.assignId(like, seq.incrementAndGet());
        }
        store.add(like);
        return like;
    }

    @Override
    public boolean exists(Long memberId, Long productId) {
        return store.stream()
            .anyMatch(l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId));
    }

    @Override
    public void delete(Long memberId, Long productId) {
        store.removeIf(
            l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId));
    }

    @Override
    public long countByProductId(Long productId) {
        return store.stream().filter(l -> l.getProductId().equals(productId)).count();
    }

    @Override
    public List<LikeModel> findByMemberId(Long memberId) {
        return store.stream().filter(l -> l.getMemberId().equals(memberId)).toList();
    }
}
