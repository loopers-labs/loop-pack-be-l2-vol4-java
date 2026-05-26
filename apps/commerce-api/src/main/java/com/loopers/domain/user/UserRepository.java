package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    boolean existsByLoginId(String loginId);

    Optional<User> findByLoginId(String loginId);

    User save(User user);
}
