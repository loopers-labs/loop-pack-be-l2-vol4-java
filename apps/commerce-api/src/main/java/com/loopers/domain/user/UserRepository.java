package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    boolean existsByLoginId(String loginId);

    Optional<UserModel> findByLoginId(String loginId);

    UserModel save(UserModel user);
}
