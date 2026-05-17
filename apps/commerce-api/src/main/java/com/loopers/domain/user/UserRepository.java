package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel user);
    Optional<UserModel> findById(Long id);
    Optional<UserModel> findByIdForUpdate(Long id);
    Optional<UserModel> findByLoginId(LoginId loginId);
    boolean existsByLoginId(LoginId loginId);
}
