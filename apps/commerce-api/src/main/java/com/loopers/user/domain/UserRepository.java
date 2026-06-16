package com.loopers.user.domain;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByLoginId(String loginId);
    boolean existsById(Long id);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    User save(User user);
}
