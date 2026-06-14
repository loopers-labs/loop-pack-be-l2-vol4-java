package com.loopers.user.domain;

import com.loopers.user.domain.vo.LoginId;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long userId);
    Optional<User> findByLoginId(LoginId loginId);
}
