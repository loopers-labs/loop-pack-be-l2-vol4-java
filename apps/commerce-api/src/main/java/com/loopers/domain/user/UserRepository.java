package com.loopers.domain.user;

public interface UserRepository {

    boolean existsByLoginId(LoginId loginId);

    UserModel save(UserModel user);
}
