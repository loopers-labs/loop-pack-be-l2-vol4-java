package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel userModel);

    boolean existsByLoginId(LoginId loginId);

    Optional<UserModel> findById(Long id);

    Optional<UserModel> findByLoginId(LoginId loginId);
}
