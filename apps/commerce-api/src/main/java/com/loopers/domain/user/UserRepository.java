package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    Optional<UserModel> findByLoginId(String loginId);
    Optional<UserModel> findById(Long userId);
    boolean existsByLoginId(String loginId);
    UserModel save(UserModel user);
}
