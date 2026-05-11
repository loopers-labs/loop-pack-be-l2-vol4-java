package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel user);
    boolean existsByLoginId(String loginId);
    Optional<UserModel> findById(Long id);
    Optional<UserModel> findByLoginId(String loginId);
}