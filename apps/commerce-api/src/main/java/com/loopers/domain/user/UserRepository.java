package com.loopers.domain.user;

import com.loopers.domain.user.vo.LoginId;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long userId);
    Optional<User> findByLoginId(LoginId loginId);
}
