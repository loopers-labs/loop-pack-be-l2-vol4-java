package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean existsByUserIdAndProductId(UUID userId, UUID productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public Page<LikeModel> findAllByUserIdWithProduct(UUID userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserIdWithProduct(userId, pageable);
    }

    @Override
    public int insertIgnore(UUID userId, UUID productId) {
        return likeJpaRepository.insertIgnore(
            toBytes(UUID.randomUUID()), toBytes(userId), toBytes(productId), LocalDateTime.now());
    }

    @Override
    public int deleteIfExists(UUID userId, UUID productId) {
        return likeJpaRepository.deleteByUserIdAndProductIdReturningCount(userId, productId);
    }

    /** UUID → BINARY(16) byte[] (Hibernate 기본 UUID-binary 레이아웃: msb→lsb) */
    private static byte[] toBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
}
