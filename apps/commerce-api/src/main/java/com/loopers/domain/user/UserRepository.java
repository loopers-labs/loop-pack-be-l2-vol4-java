package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    boolean existsByUserid(String userid);
    UserModel save(UserModel user);
    Optional<UserModel> findByUserid(String userid);
}
