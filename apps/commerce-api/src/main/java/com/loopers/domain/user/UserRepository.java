package com.loopers.domain.user;

public interface UserRepository {
    boolean existsByLoginId(String loginId);
    UserModel save(UserModel userModel);
}
