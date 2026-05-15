package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;

import java.lang.reflect.Field;
import java.util.*;

public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, UserModel> data = new HashMap<>();
    private long sequence = 1L;

    @Override
    public UserModel save(UserModel userModel) {
        long id = sequence++;
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userModel, id);
            idField.setAccessible(false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        data.put(id, userModel);
        return userModel;
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return data.values().stream()
                .filter(userModel -> userModel.getLoginId().equals(loginId))
                .findFirst();
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return data.values().stream()
                .anyMatch(userModel -> userModel.getLoginId().equals(loginId));
    }
}
