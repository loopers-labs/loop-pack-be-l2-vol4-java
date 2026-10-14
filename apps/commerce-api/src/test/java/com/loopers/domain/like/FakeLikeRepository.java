package com.loopers.domain.like;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeLikeRepository implements LikeRepository {

    private final List<LikeModel> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public LikeModel save(LikeModel like) {
        // UNIQUE 제약 흉내
        if (existsByUserIdAndProductId(like.getUserId(), like.getProductId())) {
            return findByUserIdAndProductId(like.getUserId(), like.getProductId()).orElseThrow();
        }
        if (like.getId() == null || like.getId() == 0L) {
            assignId(like, sequence.incrementAndGet());
        }
        store.add(like);
        return like;
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return store.stream()
            .filter(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId))
            .findFirst();
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return findByUserIdAndProductId(userId, productId).isPresent();
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        int before = store.size();
        Iterator<LikeModel> it = store.iterator();
        while (it.hasNext()) {
            LikeModel l = it.next();
            if (l.getUserId().equals(userId) && l.getProductId().equals(productId)) {
                it.remove();
            }
        }
        return before - store.size();
    }

    @Override
    public List<LikeModel> findAllByUserId(Long userId) {
        return store.stream()
            .filter(l -> l.getUserId().equals(userId))
            .toList();
    }

    private static void assignId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("BaseEntity")) {
                clazz = clazz.getSuperclass();
            }
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Fake Repository: ID 주입 실패", e);
        }
    }
}
