package com.loopers.domain.user;

public interface UserRepository {
    UserModel save(UserModel userModel);

    boolean existsByLoginId(String loginId);
}
