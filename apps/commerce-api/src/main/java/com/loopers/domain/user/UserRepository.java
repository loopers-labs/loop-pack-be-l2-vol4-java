package com.loopers.domain.user;

import com.loopers.domain.user.vo.UserId;

import java.util.Optional;

public interface UserRepository {
    boolean existsByUserId(UserId userId);
    UserModel save(UserModel user);
    Optional<UserModel> findByUserId(UserId userId);
}
