package com.loopers.user.domain;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel user);
    Optional<UserModel> findByLoginId(String loginId);
}
