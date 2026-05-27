package com.loopers.user.domain;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    User save(User user);
}
