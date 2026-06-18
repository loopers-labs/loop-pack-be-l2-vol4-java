package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import java.util.Optional;

public interface UserRepository {
    boolean existsByLoginId(String loginId);
    UserModel save(UserModel user);
    Optional<UserModel> findByLoginId(String loginId);
}
