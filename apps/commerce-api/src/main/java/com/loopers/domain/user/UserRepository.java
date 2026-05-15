package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    boolean existsByLoginId(LoginId loginId);

    Optional<UserModel> findByLoginId(LoginId loginId);

    Optional<UserModel> findById(Long id);

    UserModel save(UserModel user);
}
