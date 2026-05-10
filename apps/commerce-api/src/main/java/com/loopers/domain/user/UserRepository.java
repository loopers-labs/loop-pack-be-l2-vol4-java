package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel userModel);

    Optional<UserModel> findByLoginId(String loginId);
}
