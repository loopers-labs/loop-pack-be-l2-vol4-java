package com.loopers.tddstudy.infrastructure.like;

import com.loopers.tddstudy.domain.like.Like;
import com.loopers.tddstudy.domain.like.LikeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository jpaRepository;

    public LikeRepositoryImpl(LikeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Like save(Like like) {
        return jpaRepository.save(like);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return jpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<Like> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public void delete(Like like) {
        jpaRepository.delete(like);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return jpaRepository.existsByUserIdAndProductId(userId, productId);
    }
}
