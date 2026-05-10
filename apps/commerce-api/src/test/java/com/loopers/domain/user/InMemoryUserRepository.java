package com.loopers.domain.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {
    private final Map<Long, UserModel> data = new HashMap<>();

    @Override
    public UserModel save(UserModel userModel) {
        data.put(userModel.getId(), userModel);
        return userModel;
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return data.values().stream()
                .filter(userModel -> userModel.getLoginId().equals(loginId))
                .findFirst();
    }
}
