package com.loopers.domain.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserModel> store = new HashMap<>();

    @Override
    public UserModel save(UserModel user) {
        store.put(user.getUserid(), user);
        return user;
    }

    @Override
    public Optional<UserModel> findByUserid(String userid) {
        return Optional.ofNullable(store.get(userid));
    }

    @Override
    public boolean existsByUserid(String userid) {
        return store.containsKey(userid);
    }
}
