package com.loopers.domain.user;

public interface UserRepository {
    UserModel save(UserModel user);
    boolean existsByLoginId(String loginId);


}
