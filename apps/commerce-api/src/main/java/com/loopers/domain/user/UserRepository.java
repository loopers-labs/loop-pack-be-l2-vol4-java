package com.loopers.domain.user;

import com.loopers.domain.user.vo.LoginId;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel user);
    Optional<UserModel> findById(Long userId);
    Optional<UserModel> findByLoginId(LoginId loginId);
}
