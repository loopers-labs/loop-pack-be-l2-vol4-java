package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.like.Like;
import com.loopers.tddstudy.domain.like.LikeRepository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class FakeLikeRepository implements LikeRepository {

    private final Map<Long, Like> store = new LinkedHashMap<>();
    private long sequence = 1L;

    @Override
    public Like save(Like like) {
        if (getId(like) == null) {
            setId(like, sequence++);
        }
        store.put(getId(like), like);
        return like;
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
                .filter(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId))
                .findFirst();
    }

    @Override
    public List<Like> findAllByUserId(Long userId) {
        return store.values().stream()
                .filter(l -> l.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Like like) {
        store.remove(getId(like));
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return store.values().stream()
                .anyMatch(l -> l.getUserId().equals(userId) && l.getProductId().equals(productId));
    }

    public void clear() {
        store.clear();
        sequence = 1L;
    }

    private Long getId(Like like) {
        try {
            Field field = Like.class.getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(like);
        } catch (Exception e) {
            throw new RuntimeException("Like id 접근 실패", e);
        }
    }

    private void setId(Like like, Long id) {
        try {
            Field field = Like.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(like, id);
        } catch (Exception e) {
            throw new RuntimeException("Like id 설정 실패", e);
        }
    }
}
