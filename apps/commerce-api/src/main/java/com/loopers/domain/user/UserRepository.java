package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    boolean existsByLoginId(LoginId loginId);

    Optional<UserModel> findByLoginIdValue(String loginIdValue);

    UserModel save(UserModel user);
}
